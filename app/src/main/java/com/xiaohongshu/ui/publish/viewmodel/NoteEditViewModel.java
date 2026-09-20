package com.xiaohongshu.ui.publish.viewmodel;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.xiaohongshu.ui.publish.model.NoteModel;
import com.xiaohongshu.ui.publish.repository.NoteRepository;
import com.xiaohongshu.ui.publish.TextToImageConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoteEditViewModel extends ViewModel {
    private final AtomicBoolean submitting = new AtomicBoolean(false);
    private final MutableLiveData<NoteModel> currentNote = new MutableLiveData<>();
    private final MutableLiveData<List<String>> imageUris = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> topics = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> location = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPublic = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> isPublishing = new MutableLiveData<>(false);
    private final MutableLiveData<String> publishResult = new MutableLiveData<>();
    
    private NoteRepository repository;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void init(Context context) {
        repository = NoteRepository.getInstance(context);
    }

    public void reloadServerNote(String id) {
        executorService.execute(() -> {
            try { repository.reloadServerNote(id); loadNote(id); }
            catch(Exception error) { publishResult.postValue("加载失败："+error.getMessage()); }
        });
    }
    public void loadNote(String noteId) {
        executorService.execute(() -> {
            NoteModel note = repository.getNoteById(noteId);
            if (note != null) {
                currentNote.postValue(note);
                imageUris.postValue(new ArrayList<>(note.getImageUris()));
                topics.postValue(new ArrayList<>(note.getTopics()));
                location.postValue(note.getLocation());
                isPublic.postValue(note.isPublic());
            }
        });
    }

    public void initializeNewNote(String initialContent) {
        NoteModel note = new NoteModel();
        note.setContent(initialContent);
        currentNote.postValue(note);
        imageUris.postValue(new ArrayList<>());
        topics.postValue(new ArrayList<>());
        location.postValue(null);
        isPublic.postValue(true);
    }

    public LiveData<NoteModel> getCurrentNote() {
        return currentNote;
    }

    public LiveData<List<String>> getImageUris() {
        return imageUris;
    }

    public LiveData<List<String>> getTopics() {
        return topics;
    }

    public LiveData<String> getLocation() {
        return location;
    }

    public LiveData<Boolean> getIsPublic() {
        return isPublic;
    }

    public LiveData<Boolean> getIsPublishing() {
        return isPublishing;
    }

    public LiveData<String> getPublishResult() {
        return publishResult;
    }

    public void setTitle(String title) {
        NoteModel note = currentNote.getValue();
        if (note != null) {
            note.setTitle(title);
            currentNote.postValue(note);
        }
    }

    public void setContent(String content) {
        NoteModel note = currentNote.getValue();
        if (note != null) {
            note.setContent(content);
            currentNote.postValue(note);
        }
    }

    public void addImageUri(String uri) {
        List<String> uris = imageUris.getValue();
        if (uris == null) {
            uris = new ArrayList<>();
        } else {
            uris = new ArrayList<>(uris);
        }
        uris.removeIf(TextToImageConverter::isGeneratedTextImage);
        if (!uris.contains(uri)) {
            uris.add(uri);
            imageUris.postValue(uris);
            
            NoteModel note = currentNote.getValue();
            if (note != null) {
                note.setImageUris(uris);
                currentNote.postValue(note);
            }
        }
    }

    public void removeImageUri(String uri) {
        List<String> uris = imageUris.getValue();
        if (uris != null && uris.contains(uri)) {
            uris = new ArrayList<>(uris);
            uris.remove(uri);
            imageUris.postValue(uris);
            
            NoteModel note = currentNote.getValue();
            if (note != null) {
                note.setImageUris(uris);
                currentNote.postValue(note);
            }
        }
    }

    public void addTopics(List<String> additions) {
        List<String> combined = new ArrayList<>(topics.getValue() == null ? new ArrayList<>() : topics.getValue());
        for (String topic : additions) {
            if (topic != null && !topic.trim().isEmpty() && !combined.contains(topic)) combined.add(topic);
        }
        topics.setValue(combined);
        NoteModel note = currentNote.getValue();
        if (note != null) {
            note.setTopics(combined);
            currentNote.setValue(note);
        }
    }

    public void addTopic(String topic) {
        List<String> topicList = topics.getValue();
        if (topicList == null) {
            topicList = new ArrayList<>();
        } else {
            topicList = new ArrayList<>(topicList);
        }
        if (!topicList.contains(topic)) {
            topicList.add(topic);
            topics.postValue(topicList);
            
            NoteModel note = currentNote.getValue();
            if (note != null) {
                note.setTopics(topicList);
                currentNote.postValue(note);
            }
        }
    }

    public void removeTopic(String topic) {
        List<String> topicList = topics.getValue();
        if (topicList != null && topicList.contains(topic)) {
            topicList = new ArrayList<>(topicList);
            topicList.remove(topic);
            topics.postValue(topicList);
            
            NoteModel note = currentNote.getValue();
            if (note != null) {
                note.setTopics(topicList);
                currentNote.postValue(note);
            }
        }
    }

    public void setLocation(String locationName) {
        location.postValue(locationName);
        
        NoteModel note = currentNote.getValue();
        if (note != null) {
            note.setLocation(locationName);
            currentNote.postValue(note);
        }
    }

    public void setPublic(boolean isPublic) {
        this.isPublic.postValue(isPublic);
        
        NoteModel note = currentNote.getValue();
        if (note != null) {
            note.setPublic(isPublic);
            currentNote.postValue(note);
        }
    }

    public void saveDraft() {
        NoteModel note = currentNote.getValue();
        if (note == null || repository == null) {
            return;
        }
        if (!submitting.compareAndSet(false, true)) return;
        isPublishing.setValue(true);
        
        // Update note with current state
        note.setImageUris(imageUris.getValue() != null ? imageUris.getValue() : new ArrayList<>());
        note.setTopics(topics.getValue() != null ? topics.getValue() : new ArrayList<>());
        note.setLocation(location.getValue());
        note.setPublic(isPublic.getValue() != null && isPublic.getValue());
        
        executorService.execute(() -> {
            try {
                publishResult.postValue(repository.saveDraft(note));
            } catch (Exception e) {
                publishResult.postValue("保存失败：" + e.getMessage());
                loadNote(note.getId());
            } finally {
                submitting.set(false);
                isPublishing.postValue(false);
            }
        });
    }

    public void publishNote() {
        NoteModel note = currentNote.getValue();
        if (note == null || repository == null) {
            publishResult.postValue("发布失败：笔记数据为空");
            return;
        }
        
        // Validate required fields
        String title = note.getTitle();
        String content = note.getContent();
        if ((title == null || title.trim().isEmpty()) && 
            (content == null || content.trim().isEmpty())) {
            publishResult.postValue("发布失败：请输入标题或内容");
            return;
        }
        
        if (!submitting.compareAndSet(false, true)) return;
        isPublishing.setValue(true);
        
        // Update note with current state
        note.setImageUris(imageUris.getValue() != null ? imageUris.getValue() : new ArrayList<>());
        note.setTopics(topics.getValue() != null ? topics.getValue() : new ArrayList<>());
        note.setLocation(location.getValue());
        note.setPublic(isPublic.getValue() != null && isPublic.getValue());
        
        executorService.execute(() -> {
            try {
                publishResult.postValue(repository.publishNote(note));
            } catch (Exception e) {
                publishResult.postValue("发布失败：" + e.getMessage());
                loadNote(note.getId());
            } finally {
                submitting.set(false);
                isPublishing.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

