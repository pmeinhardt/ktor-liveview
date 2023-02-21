import resolve from "./resolve";
import Socket from "./Socket";
import View from "./View";

function connect(endpoint: string) {
  const socket = new Socket(resolve(endpoint));
  const view = new View(socket, document.documentElement);

  view.setup();
  view.join();

  socket.connect();
}

export default function (endpoint: string) {
  if (["complete", "interactive", "loaded"].includes(document.readyState)) {
    connect(endpoint);
  } else {
    document.addEventListener("DOMContentLoaded", () => connect(endpoint));
  }
}
