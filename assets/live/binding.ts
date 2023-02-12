const binding = (key: string): string => `data-ktor-${key}`;

export const one = (root: HTMLElement, key: string) =>
  root.querySelector(`[${binding(key)}]`);

export const all = (root: HTMLElement, key: string) =>
  Array.from(root.querySelectorAll(`[${binding(key)}]`));

export const attr = (node: Element, key: string) =>
  node.getAttribute(binding(key));

export default binding;
