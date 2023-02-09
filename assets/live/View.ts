import type Socket from "./Socket";

export default class View {
  private readonly socket: Socket;
  private readonly root: HTMLElement;

  constructor(socket: Socket, root: HTMLElement) {
    this.socket = socket;
    this.root = root;
  }

  join() {
    const socket = this.socket;

    socket.on("open", this.onconnect);
    socket.on("close", this.ondisconnect);
    socket.on("message", this.onmessage);
  }

  protected onconnect = () => {
    const path = window.location.pathname;
    const parameters = { name: "Firefox" };
    this.socket.send(JSON.stringify({ path, parameters }));
  };

  protected ondisconnect = () => {};

  protected onmessage = (event) => {
    console.log(event.data);
  };
}
