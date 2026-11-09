// Exercise a Supabase user worker, including its module loader and resource limits.
Deno.serve(async (request: Request) => {
  const servicePath = Deno.env.get("KOYANE_SERVICE_PATH")!;
  const worker = await EdgeRuntime.userWorkers.create({
    servicePath,
    memoryLimitMb: 256,
    workerTimeoutMs: 60_000,
    noModuleCache: false,
    envVars: [],
    forceCreate: false,
    cpuTimeSoftLimitMs: 2_000,
    cpuTimeHardLimitMs: 5_000,
    staticPatterns: [`${servicePath}/kotlin/*.wasm`],
  });
  return worker.fetch(request);
});
