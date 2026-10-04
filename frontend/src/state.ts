// Simple pub/sub store for vanilla TS state management.
// Trade-off vs React: no automatic VDOM diff — render() rebuilds the DOM from scratch.
// For 10-50 posts this is fast enough; if perf becomes an issue, switch to targeted updates.

import type { Post, CreatePostRequest, UpdatePostRequest } from './types';
import { DEFAULT_PAGE } from './constants';

type Listener = () => void;

class Store<T> {
  private listeners = new Set<Listener>();
  constructor(public state: T) {}
  update(producer: (s: T) => T): void {
    this.state = producer(this.state);
    this.listeners.forEach((l) => l());
  }
  subscribe(l: Listener): () => void {
    this.listeners.add(l);
    return () => {
      this.listeners.delete(l);
    };
  }
}

export interface AppState {
  posts: Post[];
  loading: boolean;
  error: string | null;
  showForm: boolean;
  editingPost: Post | null;
  searchKeyword: string;
  currentPage: number;
  totalPages: number;
  totalElements: number;
  showAll: boolean;
}

export const initialState: AppState = {
  posts: [],
  loading: false,
  error: null,
  showForm: false,
  editingPost: null,
  searchKeyword: '',
  currentPage: DEFAULT_PAGE,
  totalPages: 0,
  totalElements: 0,
  showAll: false,
};

export const store = new Store<AppState>(initialState);

// ---- Action helpers (avoid inline reducer logic in render functions) ----

export const actions = {
  setLoading(loading: boolean): void {
    store.update((s) => ({ ...s, loading }));
  },
  setError(error: string | null): void {
    store.update((s) => ({ ...s, error }));
  },
  setPosts(posts: Post[], totalPages: number, totalElements: number): void {
    store.update((s) => ({ ...s, posts, totalPages, totalElements }));
  },
  setPage(page: number): void {
    store.update((s) => ({ ...s, currentPage: page }));
  },
  setShowAll(showAll: boolean): void {
    store.update((s) => ({ ...s, showAll, currentPage: DEFAULT_PAGE }));
  },
  setSearchKeyword(keyword: string): void {
    store.update((s) => ({ ...s, searchKeyword: keyword }));
  },
  showCreateForm(): void {
    store.update((s) => ({ ...s, showForm: true, editingPost: null }));
  },
  showEditForm(post: Post): void {
    store.update((s) => ({ ...s, showForm: true, editingPost: post }));
  },
  closeForm(): void {
    store.update((s) => ({ ...s, showForm: false, editingPost: null }));
  },
  /** Optimistic-ish: bump likeCount locally; rollback on failure. */
  bumpLike(id: number, delta: number): void {
    store.update((s) => ({
      ...s,
      posts: s.posts.map((p) => (p.id === id ? { ...p, likeCount: p.likeCount + delta } : p)),
    }));
  },
  /** After form submit success: refresh list and close form. */
  refreshAfterMutation(): void {
    store.update((s) => ({ ...s, showForm: false, editingPost: null }));
  },
};

export type FormData = CreatePostRequest | UpdatePostRequest;
