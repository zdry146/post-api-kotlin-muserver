// Entry point — finds #app element and mounts the application.

import { mountApp } from './app';

const root = document.getElementById('app');
if (!root) {
  throw new Error('#app element not found in index.html');
}

mountApp(root);
