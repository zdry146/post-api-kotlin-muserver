// post-item — single post card. Equivalent of React's PostItem component.
//
// All user-supplied content (title, content, authorName) uses textContent (via h() helpers)
// to prevent XSS. Only className and inner structure come from our code.

import { h } from '../dom';
import type { Post } from '../types';

interface PostItemHandlers {
  onEdit: (post: Post) => void;
  onDelete: (id: number) => void;
  onTogglePublish: (id: number) => void;
  onLike: (id: number) => void;
  onUnlike: (id: number) => void;  // future-proof: omo's backend P1 #2
}

function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleDateString();
  } catch {
    return iso;
  }
}

export function renderPostItem(post: Post, handlers: PostItemHandlers): HTMLElement {
  return h(
    'div',
    {
      class: `post-card ${post.isDeleted ? 'deleted' : ''}`,
      dataset: { postId: String(post.id) },
    },
    h(
      'div',
      { class: 'post-header' },
      h('h3', { class: 'post-title' }, post.title),
      h(
        'span',
        { class: 'post-status' },
        post.isPublished ? '已发布' : '草稿',
      ),
    ),
    h('p', { class: 'post-content' }, post.content),
    h(
      'div',
      { class: 'post-meta' },
      h('span', null, post.authorName),
      h('span', null, `${post.viewCount} 次浏览`),
      h('span', null, `${post.likeCount} 点赞`),
      h('span', null, formatDate(post.createdAt)),
    ),
    h(
      'div',
      { class: 'post-actions' },
      h(
        'button',
        {
          class: 'btn-secondary',
          onclick: () => handlers.onLike(post.id),
          title: '点赞',
        },
        '👍 点赞',
      ),
      h(
        'button',
        {
          class: 'btn-secondary',
          onclick: () => handlers.onUnlike(post.id),
          title: '取消点赞（需后端 /unlike 支持）',
        },
        '👎 取消',
      ),
      h(
        'button',
        {
          class: 'btn-secondary',
          onclick: () => handlers.onTogglePublish(post.id),
        },
        post.isPublished ? '取消发布' : '发布',
      ),
      h(
        'button',
        {
          class: 'btn-primary',
          onclick: () => handlers.onEdit(post),
        },
        '编辑',
      ),
      h(
        'button',
        {
          class: 'btn-danger',
          onclick: () => handlers.onDelete(post.id),
        },
        '删除',
      ),
    ),
  );
}
