import resolve from "./resolve";
import Socket from "./Socket";

export function setup(endpoint: string) {
  if (["complete", "interactive", "loaded"].includes(document.readyState)) {
    connect(endpoint);
  } else {
    document.addEventListener("DOMContentLoaded", () => connect(endpoint));
  }
}

export function connect(endpoint: string) {
  const socket = new Socket(resolve(endpoint));

  socket.on("open", (event) => {
    console.debug("socket open:", event);
  });

  socket.on("close", (event) => {
    console.debug("socket close:", event);
  });

  socket.on("message", (event) => {
    console.debug("socket message:", event);
  });

  socket.on("error", (event) => {
    console.error("socket error:", event);
  });

  socket.connect();

  return socket;
}
