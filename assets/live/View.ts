import morphdom from "morphdom";

import { all, attr } from "./binding";
import type Socket from "./Socket";

export default class View {
  private readonly socket: Socket;
  private readonly root: HTMLElement;

  constructor(socket: Socket, root: HTMLElement) {
    this.socket = socket;
    this.root = root;
  }

  setup() {
    all(this.root, "click").forEach((node) => {
      node.addEventListener("click", () => {
        const data = JSON.stringify({
          type: "invoke",
          identifier: attr(node, "click"),
        });
        this.socket.send(data);
      });
    });
  }

  join() {
    const socket = this.socket;

    socket.on("open", this.onconnect);
    socket.on("close", this.ondisconnect);
    socket.on("message", this.onmessage);
  }

  protected onconnect = () => {
    const path = window.location.pathname;
    const state = JSON.parse(attr(this.root, "state"));
    this.socket.send(JSON.stringify({ path, state }));
  };

  protected ondisconnect = () => {
    // ?
  };

  protected onmessage = (event) => {
    const message = JSON.parse(event.data);
    morphdom(this.root, message.html);
  };
}
