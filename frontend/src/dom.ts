// Tiny h() helper — creates DOM nodes with attributes, classes, and event listeners.
// Similar to Preact's h() but no VDOM, no JSX. Direct DOM construction.
//
// Usage:
//   h('div', { class: 'app', onclick: handleClick },
//     h('h1', null, 'Title'),
//     'Some text',
//   )

type EventName = `on${Capitalize<string>}`;

type ElementProps<T extends keyof HTMLElementTagNameMap> = Partial<
  Omit<HTMLElementTagNameMap[T], 'style' | 'dataset'>
> & {
  class?: string;
  className?: string;
  style?: Partial<CSSStyleDeclaration>;
  dataset?: Record<string, string | number>;
  children?: (Node | string | null | undefined | false)[];
} & {
  // Event handlers: loosely typed as Event for ergonomics.
  // addEventListener receives the right concrete event at runtime.
  [K in EventName]?: (event: Event) => void;
} & {
  // data-* attributes for testing (e.g. data-testid for Playwright).
  // TypeScript template literal types are limited, so we allow any
  // string-keyed value here; dom.ts routes `data-*` keys to setAttribute.
  [key: `data-${string}`]: string | number | boolean | undefined;
};

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props?: ElementProps<K> | null,
  ...children: (Node | string | null | undefined | false)[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);

  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value === null || value === undefined || value === false) continue;
      if (key === 'class' || key === 'className') {
        node.className = String(value);
      } else if (key === 'style' && typeof value === 'object') {
        Object.assign(node.style, value as CSSStyleDeclaration);
      } else if (key === 'dataset' && typeof value === 'object') {
        for (const [dk, dv] of Object.entries(value as Record<string, string | number>)) {
          node.dataset[dk] = String(dv);
        }
      } else if (key === 'children') {
        // handled below
        continue;
      } else if (key.startsWith('on') && typeof value === 'function') {
        const event = key.slice(2).toLowerCase();
        node.addEventListener(event, value as EventListener);
      } else if (key.startsWith('data-')) {
        // data-* attributes (e.g. data-testid) must go through setAttribute
        // so Playwright/CSS selectors can find them.
        node.setAttribute(key, String(value));
      } else {
        // Set as attribute (works for most HTMLElement props)
        try {
          (node as unknown as Record<string, unknown>)[key] = value;
        } catch {
          node.setAttribute(key, String(value));
        }
      }
    }
  }

  const allChildren = [
    ...(props?.children ?? []),
    ...children,
  ];
  for (const c of allChildren) {
    if (c === null || c === undefined || c === false) continue;
    if (typeof c === 'string') {
      node.appendChild(document.createTextNode(c));
    } else {
      node.appendChild(c);
    }
  }

  return node;
}

/** Convenience: text-only node. */
export function text(content: string): Text {
  return document.createTextNode(content);
}

/** Empty fragment for placeholders. */
export function fragment(): DocumentFragment {
  return document.createDocumentFragment();
}
