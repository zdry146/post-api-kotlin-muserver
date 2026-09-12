-- PostgreSQL Schema for Post API (Kotlin + mu-server 2.4.2 迁移版)
-- Run once on first deployment

CREATE TABLE IF NOT EXISTS posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author_name VARCHAR(50) NOT NULL,
    cover_image VARCHAR(500),
    view_count BIGINT DEFAULT 0,
    like_count BIGINT DEFAULT 0,
    is_published BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_posts_title ON posts(title);
CREATE INDEX IF NOT EXISTS idx_posts_is_published ON posts(is_published);
CREATE INDEX IF NOT EXISTS idx_posts_is_deleted ON posts(is_deleted);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at);

-- Seed data for local dev
INSERT INTO posts (title, content, author_name, cover_image, view_count, like_count, is_published, is_deleted, created_at, updated_at) VALUES
('Welcome Post', 'This is a sample welcome post for the API testing.', 'Admin', NULL, 100, 10, true, false, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '60 days'),
('Draft Post 1', 'This is an old unpublished draft that should be cleaned up.', 'User1', NULL, 0, 0, false, false, CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '45 days'),
('Draft Post 2', 'Another old unpublished draft for cleanup testing.', 'User2', NULL, 0, 0, false, false, CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '35 days'),
('Recent Draft', 'A recent draft that should NOT be cleaned up.', 'User3', NULL, 5, 0, false, false, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
('Published Article', 'This is a published article that will remain.', 'Author A', NULL, 50, 5, true, false, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days')
ON CONFLICT DO NOTHING;