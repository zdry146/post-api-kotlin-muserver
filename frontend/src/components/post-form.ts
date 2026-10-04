// post-form — create/edit form with manual validation.
//
// Replaces react-hook-form + zod with:
//   - One DOM <form> element with onsubmit handler
//   - Per-field input event listeners for inline error display
//   - Validate-on-submit, no async validation, no schema library
//
// Validation rules mirror the zod schemas in the React version.

import { h } from '../dom';
import type { Post, CreatePostRequest, UpdatePostRequest } from '../types';

interface FormState {
  title: string;
  content: string;
  authorName: string;
  coverImage: string;
  errors: Record<string, string>;
  submitting: boolean;
}

interface ValidationResult {
  ok: boolean;
  errors: Record<string, string>;
}

function validate(form: FormState, isEdit: boolean): ValidationResult {
  const errors: Record<string, string> = {};

  // Title: required, max 200 chars
  if (!form.title.trim()) errors.title = '标题不能为空';
  else if (form.title.length > 200) errors.title = '标题不能超过200字符';

  // Content: required
  if (!form.content.trim()) errors.content = '内容不能为空';

  // Author name: required on create, max 50
  if (!isEdit) {
    if (!form.authorName.trim()) errors.authorName = '作者名称不能为空';
    else if (form.authorName.length > 50) errors.authorName = '作者名称不能超过50字符';
  }

  // Cover image: optional URL
  if (form.coverImage && form.coverImage.length > 0) {
    try {
      new URL(form.coverImage);
    } catch {
      errors.coverImage = '请输入有效的URL';
    }
  }

  return { ok: Object.keys(errors).length === 0, errors };
}

export interface PostFormHandlers {
  onSubmit: (data: CreatePostRequest | UpdatePostRequest) => void | Promise<void>;
  onCancel: () => void;
}

export function renderPostForm(
  post: Post | null,
  handlers: PostFormHandlers,
): HTMLElement {
  const isEdit = post !== null;
  const initialState: FormState = {
    title: post?.title ?? '',
    content: post?.content ?? '',
    authorName: post?.authorName ?? '',
    coverImage: post?.coverImage ?? '',
    errors: {},
    submitting: false,
  };

  let state: FormState = { ...initialState };
  let titleInput!: HTMLInputElement;
  let contentInput!: HTMLTextAreaElement;
  let authorInput!: HTMLInputElement;
  let coverInput!: HTMLInputElement;
  let errorRow!: HTMLDivElement;
  let submitBtn!: HTMLButtonElement;
  let formEl!: HTMLFormElement;

  function updateErrors(): void {
    errorRow.innerHTML = '';
    for (const [field, msg] of Object.entries(state.errors)) {
      const errSpan = h('span', {
        class: 'error-text',
        dataset: { field },
      }, `${labelOf(field)}: ${msg}`);
      errorRow.appendChild(errSpan);
    }
    submitBtn.disabled = state.submitting;
    submitBtn.textContent = state.submitting
      ? '提交中...'
      : isEdit
      ? '保存'
      : '创建';
  }

  function onFieldChange(): void {
    state = {
      ...state,
      title: titleInput.value,
      content: contentInput.value,
      authorName: authorInput.value,
      coverImage: coverInput.value,
      // Clear that field's error on edit
      errors: clearErrorForField(state.errors, getActiveField()),
    };
    updateErrors();
  }

  function getActiveField(): string {
    if (document.activeElement === titleInput) return 'title';
    if (document.activeElement === contentInput) return 'content';
    if (document.activeElement === authorInput) return 'authorName';
    if (document.activeElement === coverInput) return 'coverImage';
    return '';
  }

  function clearErrorForField(
    errors: Record<string, string>,
    field: string,
  ): Record<string, string> {
    if (!field || !errors[field]) return errors;
    const next = { ...errors };
    delete next[field];
    return next;
  }

  function labelOf(field: string): string {
    return {
      title: '标题',
      content: '内容',
      authorName: '作者名称',
      coverImage: '封面图片URL',
    }[field] ?? field;
  }

  async function onFormSubmit(e: Event): Promise<void> {
    e.preventDefault();

    // Read values directly from inputs at submit time, NOT from `state`.
    // Reason: Playwright's `page.fill()` does set the input value via JS +
    // dispatch input event, but in some timing/event-ordering cases the
    // onFieldChange handler may not have updated `state` by submit time
    // (e.g., the test fills and immediately clicks submit in a tight loop,
    // or the synthetic input event is suppressed). Reading DOM directly
    // is the single source of truth and matches what the user sees.
    const title = titleInput.value.trim();
    const content = contentInput.value.trim();
    // CRITICAL: authorInput is only assigned when the author field is
    // rendered (create mode, !isEdit). In edit mode the variable is
    // declared (`let authorInput!: HTMLInputElement`) but never assigned,
    // so accessing `.value` would throw TypeError. Skip the read in edit.
    const authorName = isEdit ? '' : authorInput.value.trim();
    const coverImage = coverInput.value.trim();

    const formSnapshot: FormState = { ...state, title, content, authorName, coverImage };
    const result = validate(formSnapshot, isEdit);
    if (!result.ok) {
      state = { ...state, title, content, authorName, coverImage, errors: result.errors };
      updateErrors();
      return;
    }
    state = { ...state, title, content, authorName, coverImage, submitting: true, errors: {} };
    updateErrors();
    try {
      if (isEdit) {
        const updateData: UpdatePostRequest = {
          title,
          content,
          coverImage: coverImage || undefined,
        };
        await handlers.onSubmit(updateData);
      } else {
        const createData: CreatePostRequest = {
          title,
          content,
          authorName,
          coverImage: coverImage || undefined,
        };
        await handlers.onSubmit(createData);
      }
    } catch (err) {
      // Reset on failure so user can retry
      state = { ...state, submitting: false };
      updateErrors();
      console.error('Form submit failed:', err);
    }
  }

  // Build form structure
  const authorField = !isEdit
    ? h(
        'div',
        { class: 'form-field' },
        h('label', { class: 'form-label' }, '作者名称 *'),
        (authorInput = h('input', {
          type: 'text',
          class: 'form-input',
          'data-testid': 'form-author',
          value: state.authorName,
          oninput: onFieldChange,
          required: true,
          autocomplete: 'off',
        })),
      )
    : null;

  // NOTE: closure assignments for refs (`titleInput = h(...)`) require
  // assignment expressions. h() returns the node, so the wrapping
  // `(titleInput = h(...))` pattern works. We cast to suppress
  // "noUnusedExpressions" if it bites.

  formEl = h(
    'form',
    { onsubmit: onFormSubmit },
    authorField,
    h(
      'div',
      { class: 'form-field' },
      h('label', { class: 'form-label' }, '标题 *'),
      (titleInput = h('input', {
        type: 'text',
        class: 'form-input',
        'data-testid': 'form-title',
        value: state.title,
        oninput: onFieldChange,
        required: true,
        autocomplete: 'off',
      })),
    ),
    h(
      'div',
      { class: 'form-field' },
      h('label', { class: 'form-label' }, '内容 *'),
      (contentInput = h('textarea', {
        class: 'form-textarea',
        'data-testid': 'form-content',
        rows: 6,
        oninput: onFieldChange,
        required: true,
      }, state.content)),
    ),
    h(
      'div',
      { class: 'form-field' },
      h('label', { class: 'form-label' }, '封面图片 URL'),
      (coverInput = h('input', {
        type: 'text',
        class: 'form-input',
        'data-testid': 'form-cover',
        value: state.coverImage,
        oninput: onFieldChange,
        placeholder: '可选',
        autocomplete: 'off',
      })),
    ),
    (errorRow = h('div', { class: 'form-errors' })),
    h(
      'div',
      { class: 'form-actions' },
      (submitBtn = h(
        'button',
        { type: 'submit', class: 'btn-primary' },
        isEdit ? '保存' : '创建',
      )),
      h(
        'button',
        { type: 'button', class: 'btn-secondary', onclick: handlers.onCancel },
        '取消',
      ),
    ),
  );

  return h(
    'div',
    { class: 'form-overlay' },
    h(
      'div',
      { class: 'form-modal' },
      h('h2', { class: 'form-title' }, isEdit ? '编辑帖子' : '新建帖子'),
      formEl,
    ),
  );
}
