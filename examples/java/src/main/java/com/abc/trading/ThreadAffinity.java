package com.abc.trading;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * This approach uses only standard JDK APIs + the taskset command and proc (Linux-specific). It does not 
 * require external Java native libraries.
 * ThreadAffinity temporarily renames the Java thread to locate the matching proc thread entry; the name
 * is restored afterward.
 * 
 * taskset must be available, and the process must have permission to set affinity (usually allowed for same
 * -user thread).
 * 
 * Affinity mapping users Thread.getId()%cores to produce a deterministic core choices; you can customize the
 * mapping if you want specific core numbers.
 * 
 * ThreadAffinity
 */
public final class ThreadAffinity {

    private ThreadAffinity() {}

    /**
     * Pins the current native thread to the given CPU core using `taskset`+`/proc` lookup.
     * This method works on Linux where `/proc/self/task` lists native TIDs and `taskset` is available.
     * It changes the Java thread name to a short unique token so the native thread entry is identifiable.
     */
    public static boolean pinCurrentThreadToCpu(int cpu) {
        // short name to fit in /proc comm (15 chars typical limit)
        String token = "aff-" + Long.toHexString(System.nanoTime());
        Thread current = Thread.currentThread();
        String origName = current.getName();
        String newName = token;
        try {
            current.setName(newName);
        } catch (Exception ignored) {}

        try {
            // try to find the native tid for the thread by scanning /proc/self/task/<tid>/comm
            Path tasks = Path.of("/proc/self/task");
            if (!Files.isDirectory(tasks)) return false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            String tidFound = null;
            while (System.nanoTime() < deadline) {
                try (var stream = Files.list(tasks)) {
                    var it = stream.iterator();
                    while (it.hasNext()) {
                        Path p = it.next();
                        Path comm = p.resolve("comm");
                        if (!Files.exists(comm)) continue;
                        String commName = Files.readString(comm).trim();
                        if (newName.equals(commName)) {
                            tidFound = p.getFileName().toString();
                            break;
                        }
                    }
                }
                if (tidFound != null) break;
                Thread.sleep(5);
            }

            if (tidFound == null) return false;

            // run taskset to pin that tid to the requested cpu
            // command: taskset -p -c <cpu> <tid>
            ProcessBuilder pb = new ProcessBuilder("taskset", "-p", "-c", Integer.toString(cpu), tidFound);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        } finally {
            try { current.setName(origName); } catch (Exception ignored) {}
        }
    }
}
