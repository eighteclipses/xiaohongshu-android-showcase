CREATE DATABASE IF NOT EXISTS xiaohongshu CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE xiaohongshu;

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(30) NOT NULL UNIQUE,
  nickname VARCHAR(30) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  account_meta JSON NULL,
  avatar VARCHAR(500) NOT NULL DEFAULT '',
  avatar_uri VARCHAR(500) NOT NULL DEFAULT '',
  background VARCHAR(800) NOT NULL DEFAULT '',
  bio VARCHAR(200) NOT NULL DEFAULT '',
  coins INT NOT NULL DEFAULT 5000,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_users (
  id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(60) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL DEFAULT 'admin',
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS posts (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL DEFAULT '',
  content TEXT NOT NULL,
  images JSON NOT NULL,
  topics JSON NOT NULL,
  location VARCHAR(120) NOT NULL DEFAULT '',
  is_public TINYINT(1) NOT NULL DEFAULT 1,
  is_draft TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL DEFAULT 'approved',
  media_type VARCHAR(10) NOT NULL DEFAULT 'image',
  video_url VARCHAR(500) NOT NULL DEFAULT '',
  content_version INT NOT NULL DEFAULT 1,
  deleted_at DATETIME(3) NULL,
  moderation_meta JSON NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  INDEX idx_posts_user (user_id),
  INDEX idx_posts_feed (is_public, is_draft, created_at),
  CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS likes (
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (post_id, user_id),
  INDEX idx_likes_user (user_id),
  CONSTRAINT fk_likes_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS collections (
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (post_id, user_id),
  INDEX idx_collections_user (user_id),
  CONSTRAINT fk_collections_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_collections_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS follows (
  follower_id VARCHAR(64) NOT NULL,
  following_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (follower_id, following_id),
  INDEX idx_follows_following (following_id),
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_follows_following FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comments (
  id VARCHAR(64) PRIMARY KEY,
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_comments_post (post_id, created_at),
  CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  type VARCHAR(30) NOT NULL,
  post_id VARCHAR(64) NULL,
  read_flag TINYINT(1) NOT NULL DEFAULT 0,
  moderation_meta JSON NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_notifications_user (user_id, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS post_views (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NULL,
  visitor_key VARCHAR(128) NOT NULL,
  view_date DATE NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uq_post_view (post_id, visitor_key, view_date),
  INDEX idx_views_post (post_id, created_at),
  CONSTRAINT fk_views_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reports (
  id VARCHAR(64) PRIMARY KEY,
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'pending',
  note VARCHAR(200) NOT NULL DEFAULT '',
  handled_by VARCHAR(64) NOT NULL DEFAULT '',
  handled_at DATETIME(3) NULL,
  moderation_meta JSON NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_reports_status (status, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  admin_id VARCHAR(64) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  detail JSON NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS direct_messages (
  id VARCHAR(64) PRIMARY KEY,
  from_id VARCHAR(64) NOT NULL,
  to_id VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_dm_pair (from_id, to_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS moderation_rules (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS moderation_cases (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS moderation_jobs (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS moderation_events (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS import_receipts (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_metadata (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB;
