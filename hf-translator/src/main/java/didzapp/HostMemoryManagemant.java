package didzapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;

import didzapp.hf_translator.Translator;
import didzapp.hf_translator.Translator.Platform;

public class HostMemoryManagemant {
	private static long memoryLimitBytes = 500 * 1024L * 1024L; // 1-GB default
	private static AtomicBoolean autoRefreshMemoryLimits = new AtomicBoolean(
			false);
	private final static AtomicLong reservedMemory = new AtomicLong(
			0);
	private final static AtomicBoolean limitMemoryUsage = new AtomicBoolean(
			false);
	private static final AtomicLong totalNetMemoryUsage = new AtomicLong(
			0);
	private static final AtomicLong totalCompletedTasks = new AtomicLong(
			0);
	private static final AtomicLong totalNetMemoryUsageAverage = new AtomicLong(
			0);
	private final static AtomicLong taskIds = new AtomicLong(
			0);
	private final static Map<Long, TaskReservation> activeTasks = new ConcurrentHashMap<>();
	private final static Map<String, TaskStatistics> taskStatistics = new ConcurrentHashMap<>();
	private static long refreshRate = 5_000L;
	private static volatile long lastMemoryCheckTimestamp = 0;
	private static final Object memoryCheckLock = new Object();
	private static final List<GarbageCollectorMXBean> garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans();
	private static final AtomicLong totalGarbageCollections = new AtomicLong(
			0);
	private static final AtomicLong totalGarbageCollectionTime = new AtomicLong(
			0);
	private static final AtomicLong totalGarbageCollectedBytes = new AtomicLong(
			0);
	private static final AtomicLong previousHeapUsage = new AtomicLong(
			0);
	private final static AtomicBoolean resourceHalvinglock = new AtomicBoolean(
			false);

	private static void updateGarbageCollectionStats() {
		long collections = 0;
		long collectionTime = 0;
		for (GarbageCollectorMXBean bean : garbageCollectors) {
			long count = bean.getCollectionCount();
			long time = bean.getCollectionTime();
			if (count >= 0) {
				collections += count;
			}
			if (time >= 0) {
				collectionTime += time;
			}
		}
		totalGarbageCollections.set(collections);
		totalGarbageCollectionTime.set(collectionTime);
	}

	private static void updateGarbageCollectedBytes() {
		long currentHeapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		long previous = previousHeapUsage.getAndSet(currentHeapUsage);
		if (previous > currentHeapUsage) {
			totalGarbageCollectedBytes.addAndGet(previous - currentHeapUsage);
		}
	}

	/**
	 * Sets the maximum amount of memory for application to use.
	 *
	 */
	public static void setHostMemoryLimitMB(long applicationMemoryLimitMB) {
		if (applicationMemoryLimitMB > -1) {
			memoryLimitBytes = applicationMemoryLimitMB * 1024L * 1024L;
			autoRefreshMemoryLimits.set(false);
		} else {
			memoryLimitBytes = getRecommendedMemoryBudgetMB();
			autoRefreshMemoryLimits.set(true);
		}
	}

	/**
	 * Sets the maximum amount of memory for application to use.
	 *
	 */
	public static void setHostMemoryRefreshRateSec(long refreshRateSec) {
		if (refreshRateSec > -1) {
			refreshRate = refreshRateSec * 1000;// millis
		}
	}

	/**
	 * Holds a reservation until the task finishes.
	 */
	private static class TaskReservation {
		private final long id;
		private final String taskName;
		private final long memoryBytes;

		private TaskReservation(long id, String taskName, long memoryBytes) {
			this.id = id;
			this.taskName = taskName;
			this.memoryBytes = memoryBytes;
		}
	}

	/**
	 * Handle returned to the calling thread. AutoCloseable ensures reservations can
	 * be released using try-with-resources.
	 */
	public static class TaskHandle implements AutoCloseable {
		private final TaskReservation reservation;
		private final AtomicBoolean closed = new AtomicBoolean(
				false);
		private final Map<Long, Long> trackedThreads = new ConcurrentHashMap<>();
		private final Map<Long, Process> trackedProcesses = new ConcurrentHashMap<>();
		private final Map<Long, Long> trackedProcessMaxUsage = new ConcurrentHashMap<>();
		private final AtomicLong totalAllocatedBytes = new AtomicLong(
				0);
		private final ScheduledExecutorService processMonitor = Executors.newSingleThreadScheduledExecutor();

		private static ThreadMXBean getThreadMXBean() {
			return (ThreadMXBean) ManagementFactory.getThreadMXBean();
		}

		public void registerThread(Thread thread) {
			ThreadMXBean bean = getThreadMXBean();
			long threadId = thread.threadId();
			long allocated = bean.getThreadAllocatedBytes(threadId);
			if (allocated >= 0) {
				this.trackedThreads.putIfAbsent(Long.valueOf(threadId), Long.valueOf(allocated));
			}
		}

		public void registerProcess(Process process) {
			long pid = process.pid();
			this.trackedProcesses.put(Long.valueOf(pid), process);
			this.processMonitor.scheduleAtFixedRate(() -> {
				Process tracked = this.trackedProcesses.get(Long.valueOf(pid));
				if (tracked == null || !tracked.isAlive()) {
					this.trackedProcesses.remove(Long.valueOf(pid));
					return;
				}
				long allocated = Translator.platformRuningOn == Platform.WINDOWS ? getProcessMemoryBytesWindows(
						pid) : Translator.platformRuningOn == Platform.LINUX ? getProcessMemoryBytesLinux(pid) : 0L;
				if (allocated >= 0) {
					this.trackedProcessMaxUsage.put(
							Long.valueOf(pid),
							Long.valueOf(Math.max(this.trackedProcessMaxUsage.get(Long.valueOf(pid)).longValue(), allocated)));
				}
			}, 0, refreshRate, TimeUnit.MILLISECONDS);
			process.onExit().thenRun(() -> {
				this.trackedProcesses.remove(Long.valueOf(pid));
			});
		}

		private static long getProcessMemoryBytesWindows(long pid) {
			try {
				ProcessBuilder builder = new ProcessBuilder(
						"powershell.exe", //$NON-NLS-1$
						"-NoProfile", //$NON-NLS-1$
						"-NonInteractive", //$NON-NLS-1$
						"-Command", //$NON-NLS-1$
						"(Get-Process -Id " + pid + ").WorkingSet64"); //$NON-NLS-1$ //$NON-NLS-2$
				builder.redirectErrorStream(true);
				Process powershell = builder.start();
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(
								powershell.getInputStream()))) {
					String output = reader.readLine();
					if (output != null && powershell.waitFor() == 0) {
						return Long.parseLong(output.trim());
					}
				}
				powershell.destroyForcibly();
			} catch (InterruptedException | NumberFormatException | IOException e) {
				T_Log.log("Error While Reading Process Memory Usage On Windows", e); //$NON-NLS-1$
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
			}
			return -1L;
		}

		private static long getProcessMemoryBytesLinux(long pid) {
			Path status = Path.of("/proc", String.valueOf(pid), "status"); //$NON-NLS-1$ //$NON-NLS-2$
			try (Stream<String> lines = Files.lines(status)) {
				return lines.filter(line -> line.startsWith("VmRSS:")) //$NON-NLS-1$
						.map(line -> line.replace("VmRSS:", "").trim()) //$NON-NLS-1$ //$NON-NLS-2$
						.map(line -> line.replace("kB", "").trim()) //$NON-NLS-1$ //$NON-NLS-2$
						.mapToLong(Long::parseLong)
						.findFirst()
						.orElse(-1L) * 1024;
			} catch (NumberFormatException | IOException e) {
				T_Log.log("Error While Reading Process Memory Usage On Linux", e); //$NON-NLS-1$
				return -1L;
			}
		}

		public void unregisterThread(Thread thread) {
			long threadId = thread.threadId();
			Long before = this.trackedThreads.remove(Long.valueOf(threadId));
			if (before != null) {
				long after = getThreadMXBean().getThreadAllocatedBytes(threadId);
				if (after >= before.longValue()) {
					this.totalAllocatedBytes.addAndGet(after - before.longValue());
				}
			}
		}

		public long getTotalAllocatedBytes() {
			long maxProcessUsage = this.trackedProcessMaxUsage.values().stream().mapToLong(Long::longValue).sum();
			return this.totalAllocatedBytes.get() + maxProcessUsage;
		}

		private TaskHandle(TaskReservation reservation) {
			this.reservation = reservation;
		}

		public String getTaskName() {
			return this.reservation.taskName;
		}

		public long getReservedMemoryMB() {
			return this.reservation.memoryBytes / (1024L * 1024L);
		}

		public long getAverageUsageMB() {
			return getAverageTaskUsageMB(getTaskName());
		}

		public long getMaximumUsageMB() {
			return getMaximumTaskUsageMB(getTaskName());
		}

		public long getMinimumUsageMB() {
			return getMinimumTaskUsageMB(getTaskName());
		}

		@Override
		public void close() {
			if (this.closed.compareAndSet(false, true)) {
				release(this.reservation);
			}
		}
	}

	public static class TaskStatistics {
		private final AtomicLong executionCount = new AtomicLong();
		private final AtomicLong totalAllocatedBytes = new AtomicLong();
		private final AtomicLong maximumAllocatedBytes = new AtomicLong();
		private final AtomicLong minimumAllocatedBytes = new AtomicLong();

		public void recordUsage(long allocatedBytes) {
			this.totalAllocatedBytes.addAndGet(allocatedBytes);
			this.executionCount.incrementAndGet();
			this.maximumAllocatedBytes.accumulateAndGet(allocatedBytes, Math::max);
			this.minimumAllocatedBytes.accumulateAndGet(allocatedBytes, Math::min);
			if (this.totalAllocatedBytes.get() > memoryLimitBytes) {
				if (resourceHalvinglock.compareAndSet(false, true)) {
					try {
						if (this.totalAllocatedBytes.get() > memoryLimitBytes) {
							half();
						}
					} finally {
						resourceHalvinglock.set(false);
					}
				}
			}
		}

		private void half() {
			this.executionCount.set(this.executionCount.get() / 2);
			this.totalAllocatedBytes.set(this.totalAllocatedBytes.get() / 2);
		}

		public long getAverageUsageBytes() {
			long count = this.executionCount.get();
			return count == 0 ? 0 : this.totalAllocatedBytes.get() / count;
		}

		public long getMaximumUsageBytes() {
			return this.maximumAllocatedBytes.get();
		}

		public long getMinimumUsageBytes() {
			return this.minimumAllocatedBytes.get();
		}

		public long getExecutionCount() {
			return this.executionCount.get();
		}
	}

	/**
	 * Attempts to reserve memory for a task.
	 *
	 * Returns a TaskHandle if successful. Returns null if insufficient memory is
	 * available.
	 */
	private static TaskHandle tryAcquire(String taskName, long requiredMemoryMB) {
		if (autoRefreshMemoryLimits.get()) {
			refreshMemoryBudgetIfRequired();
		}
		refreshMemoryBudgetIfRequired();
		long requiredBytes = requiredMemoryMB * 1024L * 1024L;
		if (requiredBytes <= 0 || requiredBytes > memoryLimitBytes) {
			return null;
		}
		while (true) {
			long current = reservedMemory.get();
			if (requiredBytes > memoryLimitBytes - current) {
				return null;
			}
			if (reservedMemory.compareAndSet(current, current + requiredBytes)) {
				break;
			}
		}
		long id = taskIds.incrementAndGet();
		if (id > 1000000000) {
			taskIds.set(0);
		}
		TaskReservation reservation = new TaskReservation(
				id,
				taskName,
				requiredBytes);
		activeTasks.put(Long.valueOf(id), reservation);
		return new TaskHandle(
				reservation);
	}

	/**
	 * Refresh memory limits if after refresh time.
	 */
	private static void refreshMemoryBudgetIfRequired() {
		updateGarbageCollectionStats();
		updateGarbageCollectedBytes();
		long now = System.currentTimeMillis();
		if (now - lastMemoryCheckTimestamp < refreshRate) {
			return;
		}
		synchronized (memoryCheckLock) {
			now = System.currentTimeMillis();
			if (now - lastMemoryCheckTimestamp >= refreshRate) {
				long recommendedMB = getRecommendedMemoryBudgetMB();
				memoryLimitBytes = recommendedMB * 1024L * 1024L;
				lastMemoryCheckTimestamp = System.currentTimeMillis();
			}
		}
		long projectedMemoryUsage = reservedMemory
				.get() + (totalNetMemoryUsageAverage.get() + totalNetMemoryUsageAverage.get() / 10);
		limitMemoryUsage.set(projectedMemoryUsage > memoryLimitBytes);
	}

	/**
	 * Record task's memory usage.
	 */
	private static void recordTaskUsage(String taskName, long allocatedBytes) {
		HostMemoryManagemant.taskStatistics.computeIfAbsent(taskName, _ -> new TaskStatistics()).recordUsage(allocatedBytes);
	}

	/**
	 * Releases a task's memory reservation.
	 */
	private static void release(TaskReservation reservation) {
		if (activeTasks.remove(Long.valueOf(reservation.id)) != null) {
			reservedMemory.addAndGet(-reservation.memoryBytes);
		}
	}

	/**
	 * Returns the total physical RAM installed on the host.
	 */
	public static long getHostTotalMemoryMB() {
		OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		return os.getTotalMemorySize() / (1024L * 1024L);
	}

	/**
	 * Returns currently available physical RAM on the host.
	 */
	public static long getHostAvailableMemoryMB() {
		OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		return os.getFreeMemorySize() / (1024L * 1024L);
	}

	/**
	 * Returns physical RAM currently in use.
	 */
	public static long getHostUsedMemoryMB() {
		return getHostTotalMemoryMB() - getHostAvailableMemoryMB();
	}

	/**
	 * Returns the maximum heap memory available to the JVM.
	 */
	public static long getJvmMaximumMemoryMB() {
		return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
	}

	/**
	 * Returns currently used JVM heap memory.
	 */
	public static long getJvmUsedMemoryMB() {
		Runtime runtime = Runtime.getRuntime();
		return (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
	}

	/**
	 * Returns remaining JVM heap capacity.
	 */
	public static long getJvmAvailableMemoryMB() {
		return getJvmMaximumMemoryMB() - getJvmUsedMemoryMB();
	}

	/**
	 * Returns the recommended memory budget in MB, based on host and JVM
	 * availability.
	 */
	public static long getRecommendedMemoryBudgetMB() {
		long hostAvailable = getHostAvailableMemoryMB();
		long jvmAvailable = getJvmAvailableMemoryMB();
		long available = Math.min(hostAvailable, jvmAvailable);
		// Keep 20% available as a safety margin.
		return (long) (available * 0.80);
	}

	/**
	 * Returns currently reserved memory.
	 */
	public static long getReservedMemoryMB() {
		return reservedMemory.get() / (1024L * 1024L);
	}

	/**
	 * Returns remaining memory budget.
	 */
	public static long getAvailableMemoryMB() {
		return (memoryLimitBytes - reservedMemory.get()) / (1024L * 1024L);
	}

	/**
	 * Returns an integer representing the number of running tasks.
	 */
	public static int getActiveTaskCount() {
		return activeTasks.size();
	}

	/**
	 * Returns a long representing the tasks average memory usage.
	 */
	public static long getAverageTaskUsageMB(String taskName) {
		TaskStatistics statistics = taskStatistics.get(taskName);
		return statistics == null ? 0 : statistics.getAverageUsageBytes() / (1024L * 1024L);
	}

	/**
	 * Returns a long representing the tasks maxiumum memory usage.
	 */
	public static long getMaximumTaskUsageMB(String taskName) {
		TaskStatistics statistics = taskStatistics.get(taskName);
		return statistics == null ? 0 : statistics.getMaximumUsageBytes() / (1024L * 1024L);
	}

	/**
	 * Returns a long representing the tasks miniumum memory usage.
	 */
	public static long getMinimumTaskUsageMB(String taskName) {
		TaskStatistics statistics = taskStatistics.get(taskName);
		return statistics == null ? 0 : statistics.getMinimumUsageBytes() / (1024L * 1024L);
	}

	/**
	 * Returns an integer representing the current amount of running tasks.
	 */
	public static int getTaskCount() {
		return HostMemoryManagemant.taskStatistics.size();
	}

	/**
	 * Returns a map containing all TaskStatistics objects labeled by task name.
	 */
	public static Map<String, TaskStatistics> getTaskStatistics() {
		return Map.copyOf(HostMemoryManagemant.taskStatistics);
	}

	/**
	 * Execute a task if the needed memory is available and track its memory usage
	 * statistics.
	 */
	public static <T> T execute(String taskName, final Function<TaskHandle, T> action) throws Exception {
		return execute(taskName, action, -1);
	}

	/**
	 * Execute a task if the needed memory is available and track its memory usage
	 * statistics.
	 */
	public static <T> T execute(String taskName, final Function<TaskHandle, T> action, long memoryAllocationOverride) throws Exception {
		long reserve = memoryAllocationOverride > -1 ? memoryAllocationOverride : getAverageTaskUsageMB(taskName);
		if (reserve > 0) {
			reserve = reserve + (limitMemoryUsage.get() ? reserve / 5 : Math.min(reserve, memoryLimitBytes - reserve));
		} else {
			reserve = memoryLimitBytes;
		}
		try (TaskHandle handle = tryAcquire(taskName, reserve)) {
			if (handle == null) {
				throw new IllegalStateException(
						"Insufficient Resources For Task: " + taskName); //$NON-NLS-1$
			}
			long initialGarbageCollectedBytes = totalGarbageCollectedBytes.get();
			handle.registerThread(Thread.currentThread());
			try {
				return action.apply(handle);
			} finally {
				handle.unregisterThread(Thread.currentThread());
				long allocatedBytes = handle.getTotalAllocatedBytes();
				recordTaskUsage(taskName, allocatedBytes);
				updateGarbageCollectionStats();
				updateGarbageCollectedBytes();
				long JVM_Unallocated = totalGarbageCollectedBytes.get() - initialGarbageCollectedBytes;
				long taskNetMemoryUsage = allocatedBytes - JVM_Unallocated;
				long total = totalNetMemoryUsage.addAndGet(taskNetMemoryUsage);
				long count = totalCompletedTasks.incrementAndGet();
				totalNetMemoryUsageAverage.set(total / count);
				if (totalNetMemoryUsage.get() > memoryLimitBytes) {
					if (resourceHalvinglock.compareAndSet(false, true)) {
						try {
							if (totalNetMemoryUsage.get() > memoryLimitBytes) {
								half();
							}
						} finally {
							resourceHalvinglock.set(false);
						}
					}
				}
			}
		}
	}

	private static void half() {
		totalNetMemoryUsage.set(totalNetMemoryUsage.get() / 2);
		totalCompletedTasks.set(totalCompletedTasks.get() / 2);
	}

	/**
	 * Returns a string representing the current memory statistics.
	 */
	public static String printStatus() {
		StringBuilder sb = new StringBuilder();
		sb.append("===== Host Memory Statistics =====\n"); //$NON-NLS-1$
		sb.append("Host Total Memory: ").append(getHostTotalMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("Host Available Memory: ").append(getHostAvailableMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("Host Used Memory: ").append(getHostUsedMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("---------------------------------\n"); //$NON-NLS-1$
		sb.append("JVM Maximum Memory: ").append(getJvmMaximumMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("JVM Used Memory: ").append(getJvmUsedMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("JVM Available Memory: ").append(getJvmAvailableMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("---------------------------------\n"); //$NON-NLS-1$
		sb.append("Task Memory Budget: ").append(getRecommendedMemoryBudgetMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("Reserved Memory: ").append(getReservedMemoryMB()).append(" MB\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("Active Tasks: ").append(getActiveTaskCount()).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("================================="); //$NON-NLS-1$
		return sb.toString();
	}
}
