import { fetch as fetchKoyane } from "./build/worker-wasm/bridge.mjs";

export default {
  async fetch(request, env) {
    try {
      return await fetchKoyane(request, env.DB);
    } catch (error) {
      console.error(error);
      return new Response("Internal Server Error", { status: 500 });
    }
  },
};
