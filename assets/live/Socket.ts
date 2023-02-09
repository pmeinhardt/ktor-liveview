import type { Emitter, Handler } from "mitt";
import mitt from "mitt";

import type { TypedArray } from "./types";

export type SocketData = string | ArrayBuffer | Blob | TypedArray | DataView;

export type SocketEvents = {
  open: Event;
  close: CloseEvent;
  message: MessageEvent;
  error: Event;
};

export const SocketState = {
  initial: "initial",
  connecting: "connecting",
  open: "open",
  closing: "closing",
  closed: "closed",
} as const;

export default class Socket {
  readonly uri: string;

  protected socket: WebSocket | null = null;
  protected events: Emitter<SocketEvents> = mitt<SocketEvents>();

  constructor(uri: string) {
    this.uri = uri;
  }

  get state(): keyof typeof SocketState {
    if (this.socket) {
      const state = this.socket.readyState;
      if (state === 0) return SocketState.connecting;
      else if (state === 1) return SocketState.open;
      else if (state === 2) return SocketState.closing;
      else return SocketState.closed;
    }

    return SocketState.initial;
  }

  connect() {
    if (this.socket) this.disconnect();

    const socket = new WebSocket(this.uri);

    socket.addEventListener("open", this.onopen);
    socket.addEventListener("close", this.onclose);
    socket.addEventListener("error", this.onerror);
    socket.addEventListener("message", this.onmessage);

    this.socket = socket;
  }

  disconnect() {
    if (this.socket) {
      const socket = this.socket;

      socket.removeEventListener("open", this.onopen);
      socket.removeEventListener("close", this.onclose);
      socket.removeEventListener("error", this.onerror);
      socket.removeEventListener("message", this.onmessage);

      socket.close();
    }

    this.socket = null;
  }

  send(data: SocketData) {
    if (this.socket) this.socket.send(data);
  }

  on<Key extends keyof SocketEvents>(
    type: Key,
    handler: Handler<SocketEvents[Key]>
  ) {
    this.events.on(type, handler);
  }

  off<Key extends keyof SocketEvents>(
    type: Key,
    handler?: Handler<SocketEvents[Key]>
  ) {
    this.events.off(type, handler);
  }

  protected onopen = (event: Event) => {
    this.events.emit("open", event);
  };

  protected onclose = (event: CloseEvent) => {
    this.events.emit("close", event);
  };

  protected onerror = (event: Event) => {
    this.events.emit("error", event);
  };

  protected onmessage = (event: MessageEvent) => {
    this.events.emit("message", event);
  };
}
