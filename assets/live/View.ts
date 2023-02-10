import morphdom from "morphdom";

import type Socket from "./Socket";
import type { Params } from "./types";

export default class View {
  private readonly socket: Socket;
  private readonly root: HTMLElement;
  private readonly params: Params;

  constructor(socket: Socket, root: HTMLElement, params: Params) {
    this.socket = socket;
    this.root = root;
    this.params = params;
  }

  join() {
    const socket = this.socket;

    socket.on("open", this.onconnect);
    socket.on("close", this.ondisconnect);
    socket.on("message", this.onmessage);
  }

  protected onconnect = () => {
    const path = window.location.pathname;
    this.socket.send(JSON.stringify({ path, parameters: this.params }));
  };

  protected ondisconnect = () => {
    // ?
  };

  protected onmessage = (event) => {
    const message = JSON.parse(event.data);
    morphdom(this.root, message.html);
  };
}
