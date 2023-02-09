import resolve from "./resolve";
import Socket from "./Socket";
import View from "./View";

export function setup(endpoint: string) {
  if (["complete", "interactive", "loaded"].includes(document.readyState)) {
    connect(endpoint);
  } else {
    document.addEventListener("DOMContentLoaded", () => connect(endpoint));
  }
}

export function connect(endpoint: string) {
  const socket = new Socket(resolve(endpoint));
  const view = new View(socket, document.documentElement);

  view.join();

  socket.connect();

  return socket;
}
