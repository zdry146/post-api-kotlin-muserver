// app — top-level composition. Equivalent of React's App() component, but using
// vanilla DOM via the h() helper.
//
// Strategy: full DOM replacement on each state change. The DOM is small (10-50 posts)
// so this is fast enough; if perf becomes an issue, switch to targeted updates.

import { h } from './dom';
import { renderPostItem } from './components/post-item';
import { renderPostForm, type PostFormHandlers } from './components/post-form';
import { renderPagination } from './components/pagination';
import { postApi, isSuccess, NetworkError } from './services/post-api';
import { store, actions, type AppState, type FormData } from './state';
import { PAGE_SIZE, DEFAULT_PAGE } from './constants';
import type { Post } from './types';

// ---- Module-level state for non-reactive concerns ----

/** Single AbortController — cancels in-flight fetch when a new one starts. */
let currentAbort: AbortController | null = null;

/** Root element reference, captured by mount(). */
let rootEl: HTMLElement | null = null;

// ---- Action implementations ----

async function fetchPosts(state: AppState): Promise<void> {
  if (currentAbort) currentAbort.abort();
  currentAbort = new AbortController();
  const signal = currentAbort.signal;

  actions.setLoading(true);
  actions.setError(null);
  try {
    const result = state.searchKeyword.trim()
      ? await postApi.search(state.searchKeyword, state.currentPage, PAGE_SIZE, signal)
      : state.showAll
      ? await postApi.listAll(state.currentPage, PAGE_SIZE, signal)
      : await postApi.listPublished(state.currentPage, PAGE_SIZE, signal);

    if (isSuccess(result)) {
      actions.setPosts(
        result.data.content,
        result.data.totalPages,
        result.data.totalElements,
      );
    } else {
      actions.setError(result.message || '获取帖子失败');
    }
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') return;
    actions.setError(
      err instanceof NetworkError
        ? `网络错误 (${err.status}): ${err.message}`
        : '网络错误，请检查后端服务是否启动',
    );
    console.error(err);
  } finally {
    actions.setLoading(false);
  }
}

async function handleSearch(): Promise<void> {
  await fetchPosts(store.state);
}

async function handleFormSubmit(data: FormData): Promise<void> {
  const editing = store.state.editingPost;
  try {
    if (editing) {
      const result = await postApi.update(editing.id, data as Parameters<typeof postApi.update>[1]);
      if (isSuccess(result)) {
        actions.refreshAfterMutation();
        await fetchPosts(store.state);
      } else {
        alert(result.message || '更新失败');
      }
    } else {
      const result = await postApi.create(data as Parameters<typeof postApi.create>[0]);
      if (isSuccess(result)) {
        actions.refreshAfterMutation();
        await fetchPosts(store.state);
      } else {
        alert(result.message || '创建失败');
      }
    }
  } catch (err) {
    alert('提交失败，请重试');
    console.error(err);
  }
}

async function handleDelete(id: number): Promise<void> {
  if (!confirm('确定要删除这篇帖子吗？')) return;
  try {
    const result = await postApi.delete(id);
    if (isSuccess(result)) {
      await fetchPosts(store.state);
    } else {
      alert(result.message || '删除失败');
    }
  } catch (err) {
    alert('删除失败，请重试');
    console.error(err);
  }
}

async function handleTogglePublish(id: number): Promise<void> {
  try {
    const result = await postApi.togglePublish(id);
    if (isSuccess(result)) {
      await fetchPosts(store.state);
    } else {
      alert(result.message || '操作失败');
    }
  } catch (err) {
    alert('操作失败，请重试');
    console.error(err);
  }
}

async function handleLike(id: number): Promise<void> {
  try {
    const result = await postApi.like(id);
    if (isSuccess(result)) {
      await fetchPosts(store.state);
    } else {
      alert(result.message || '点赞失败');
    }
  } catch (err) {
    alert('点赞失败，请重试');
    console.error(err);
  }
}

async function handleUnlike(id: number): Promise<void> {
  try {
    const result = await postApi.unlike(id);
    if (isSuccess(result)) {
      await fetchPosts(store.state);
    } else {
      alert(result.message || '取消点赞失败');
    }
  } catch (err) {
    alert('取消点赞失败，请重试');
    console.error(err);
  }
}

function handleEdit(post: Post): void {
  actions.showEditForm(post);
}

function handleCloseForm(): void {
  actions.closeForm();
}

function handlePageChange(newPage: number): void {
  if (newPage >= 0 && newPage < store.state.totalPages) {
    actions.setPage(newPage);
    // The store subscription will re-render and trigger fetchPosts via watcher,
    // but we want explicit fetch here for predictable behavior.
    void fetchPosts({ ...store.state, currentPage: newPage });
  }
}

// ---- Render function ----

export function render(state: AppState): DocumentFragment {
  const frag = document.createDocumentFragment();

  // Header
  frag.appendChild(
    h(
      'header',
      { class: 'app-header' },
      h('h1', { class: 'app-title' }, '帖子管理系统'),
      h(
        'p',
        { class: 'app-subtitle' },
        '调用 mu-server REST API 实现增删改查 (vanilla TS + HTML5)',
      ),
      h('p', { class: 'app-total' }, `共 ${state.totalElements} 篇帖子`),
    ),
  );

  // Controls
  frag.appendChild(
    h(
      'div',
      { class: 'controls' },
      h(
        'div',
        { class: 'search-box' },
        h('input', {
          type: 'text',
          placeholder: '搜索帖子...',
          class: 'search-input',
          value: state.searchKeyword,
          style: { color: '#000000' },
          oninput: (e: Event) => {
            const input = e.target as HTMLInputElement;
            actions.setSearchKeyword(input.value);
          },
          onkeydown: (e: KeyboardEvent) => {
            if (e.key === 'Enter') void handleSearch();
          },
        }),
        h(
          'button',
          { class: 'search-btn', onclick: () => void handleSearch() },
          '搜索',
        ),
      ),
      h(
        'div',
        { class: 'actions' },
        h(
          'label',
          { class: 'checkbox-label' },
          h('input', {
            type: 'checkbox',
            checked: state.showAll,
            onchange: (e: Event) => {
              const input = e.target as HTMLInputElement;
              actions.setShowAll(input.checked);
              // After showAll toggle, fetch fresh list (page reset to DEFAULT_PAGE by setShowAll)
              void fetchPosts({ ...store.state, showAll: input.checked, currentPage: DEFAULT_PAGE });
            },
          }),
          '显示全部',
        ),
        h(
          'button',
          {
            class: 'create-btn',
            onclick: () => actions.showCreateForm(),
          },
          '+ 新建帖子',
        ),
      ),
    ),
  );

  // Error banner
  if (state.error) {
    frag.appendChild(
      h(
        'div',
        { class: 'error-banner' },
        h('span', null, state.error),
        h(
          'button',
          {
            class: 'close-error',
            onclick: () => actions.setError(null),
          },
          '×',
        ),
      ),
    );
  }

  // Loading / empty / list
  if (state.loading) {
    frag.appendChild(h('div', { class: 'loading' }, '加载中...'));
  } else if (state.posts.length === 0) {
    frag.appendChild(h('div', { class: 'empty' }, '暂无帖子'));
  } else {
    const postList = h(
      'div',
      { class: 'post-list' },
      ...state.posts.map((post) =>
        renderPostItem(post, {
          onEdit: handleEdit,
          onDelete: handleDelete,
          onTogglePublish: handleTogglePublish,
          onLike: handleLike,
          onUnlike: handleUnlike,
        }),
      ),
    );
    frag.appendChild(postList);

    const pager = renderPagination({
      currentPage: state.currentPage,
      totalPages: state.totalPages,
      onChange: handlePageChange,
    });
    if (pager) frag.appendChild(pager);
  }

  // Form modal
  if (state.showForm) {
    const handlers: PostFormHandlers = {
      onSubmit: handleFormSubmit,
      onCancel: handleCloseForm,
    };
    frag.appendChild(renderPostForm(state.editingPost, handlers));
  }

  return frag;
}

export function mountApp(root: HTMLElement): void {
  rootEl = root;

  const rerender = (): void => {
    if (!rootEl) return;
    const frag = render(store.state);
    rootEl.replaceChildren(frag);
  };

  // Subscribe to store changes
  store.subscribe(rerender);

  // Initial render + initial fetch
  rerender();
  void fetchPosts(store.state);
}
