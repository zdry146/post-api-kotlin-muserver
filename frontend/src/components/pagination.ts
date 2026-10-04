// pagination — page controls.
//
// React version uses two <button>s + a label. Vanilla version: same structure,
// direct event wiring.

import { h } from '../dom';

export interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onChange: (newPage: number) => void;
}

export function renderPagination(props: PaginationProps): HTMLElement | null {
  const { currentPage, totalPages, onChange } = props;
  if (totalPages <= 1) return null;

  const prevDisabled = currentPage === 0;
  const nextDisabled = currentPage >= totalPages - 1;

  return h(
    'div',
    { class: 'pagination' },
    h(
      'button',
      {
        class: 'page-btn',
        disabled: prevDisabled,
        onclick: () => onChange(currentPage - 1),
      },
      '上一页',
    ),
    h(
      'span',
      { class: 'page-info' },
      `第 ${currentPage + 1} / ${totalPages} 页`,
    ),
    h(
      'button',
      {
        class: 'page-btn',
        disabled: nextDisabled,
        onclick: () => onChange(currentPage + 1),
      },
      '下一页',
    ),
  );
}
