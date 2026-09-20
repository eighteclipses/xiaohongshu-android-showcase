package com.xiaohongshu;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.ui.publish.NoteEditActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ModerationRegressionTest {
    @Test public void olderResponsesCannotRestoreHiddenCachedContent() {
        AppDatabase db=AppApplication.getDatabase();String id="qa_stale_cache_"+System.nanoTime();
        NoteEntity local=note(id,"hidden",true,"synced");local.contentVersion=3;local.updateTime=500000;db.noteDao().insert(local);
        try {
            com.xiaohongshu.network.RemotePost old=new com.xiaohongshu.network.RemotePost();
            old.setId(id);old.setAuthorId("qa-owner");old.setStatus("approved");old.setContentVersion(2);old.setUpdatedAt(600000);
            com.xiaohongshu.network.ModerationSync.cache(old);
            assertEquals("hidden",db.noteDao().getNoteById(id).moderationStatus);
            old.setContentVersion(3);old.setUpdatedAt(400000);com.xiaohongshu.network.ModerationSync.cache(old);
            assertEquals("hidden",db.noteDao().getNoteById(id).moderationStatus);
        } finally { db.noteDao().delete(db.noteDao().getNoteById(id)); }
    }
    private NoteEntity note(String id,String status,boolean known,String sync) {
        NoteEntity n=new NoteEntity();n.id=id;n.userId="qa-owner";n.title=id;n.content="测试";n.isPublic=true;n.isDraft=false;
        n.moderationStatus=status;n.serverKnown=known;n.syncState=sync;return n;
    }
    @Test public void publicQueriesExcludeOfflinePendingRejectedAndRecycledContent() {
        AppDatabase db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(),AppDatabase.class).allowMainThreadQueries().build();
        try {
            db.noteDao().insert(note("approved","approved",true,"synced"));
            db.noteDao().insert(note("pending","pending",true,"synced"));
            db.noteDao().insert(note("rejected","rejected",true,"synced"));
            db.noteDao().insert(note("hidden","hidden",true,"synced"));
            db.noteDao().insert(note("offline","approved",false,"pending"));
            NoteEntity deleted=note("deleted","approved",true,"synced");deleted.deleted=true;db.noteDao().insert(deleted);
            assertEquals(1,db.noteDao().getAllPublicNotesSync().size());
            assertEquals("approved",db.noteDao().getAllPublicNotesSync().get(0).id);
            assertEquals(1,db.noteDao().searchNotes("测试").size());
            assertEquals(5,db.noteDao().getOwnSubmittedNotesSync("qa-owner").size());
        } finally { db.close(); }
    }
    @Test public void editorShowsReviewReasonForExistingPendingNote() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        AppDatabase db=AppApplication.getDatabase();String id="qa_review_banner_"+System.nanoTime();
        NoteEntity n=note(id,"pending",false,"synced");n.reviewNote="命中测试关键词，等待人工审核";n.contentVersion=4;db.noteDao().insert(n);
        Activity activity=null;
        try {
            Intent intent=new Intent(instrumentation.getTargetContext(),NoteEditActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(NoteEditActivity.KEY_NOTE_ID,id);activity=instrumentation.startActivitySync(intent);
            final Activity screen=activity;boolean[] visible={false};
            for(int i=0;i<80&&!visible[0];i++) {
                instrumentation.runOnMainSync(()->{TextView banner=screen.findViewById(R.id.moderationStatusText);visible[0]=banner.getVisibility()==View.VISIBLE&&banner.getText().toString().contains("等待人工审核");});
                if(!visible[0])Thread.sleep(100);
            }
            assertTrue("review reason must be visible in editor",visible[0]);
        } finally {
            if(activity!=null){final Activity screen=activity;instrumentation.runOnMainSync(screen::finish);}
            NoteEntity disposable=db.noteDao().getNoteById(id);if(disposable!=null)db.noteDao().delete(disposable);
        }
    }
}
