package didzapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;

import didzapp.hf_translator.Translator;
import didzapp.hf_translator.Translator.Platform;

public class HostMemoryManagemant {
	private static volatile long memoryLimitBytes = 100L * 1024L * 1024L;// 100-MB default
	private static volatile double saftyMargin = 0.10; // 10% default
	private static volatile long refreshRate = 3000L;// 3 sec default
	private static volatile long lastMemoryCheckTimestamp = 0;
	private static AtomicBoolean autoRefreshMemoryLimits = new AtomicBoolean(
			false);
	private final static AtomicLong reservedMemory = new AtomicLong(
			0);
	private static final AtomicLong totalNetMemoryUsage = new AtomicLong(
			0);
	private static final AtomicLong totalCompletedTasks = new AtomicLong(
			0);
	private final static AtomicLong taskIds = new AtomicLong(
			0);
	private final static Map<Long, TaskReservation> activeTasks = new ConcurrentHashMap<>();
	private final static Map<String, TaskStatistics> taskStatistics = new ConcurrentHashMap<>();
	private static final Object memoryCheckLock = new Object();
	private final static AtomicBoolean resourceHalvinglock = new AtomicBoolean(
			false);

	/**
	 * Sets the maximum amount of memory for application to use.
	 *
	 */
	public static void setHostMemoryLimitMB(long applicationMemoryLimitMB) {
		if (applicationMemoryLimitMB > -1) {
			try {
				memoryLimitBytes = Math.multiplyExact(applicationMemoryLimitMB, 1024L * 1024L);
			} catch (ArithmeticException _) {
				memoryLimitBytes = Long.MAX_VALUE;
			}
			autoRefreshMemoryLimits.set(false);
		} else {
			memoryLimitBytes = Math.multiplyExact(getRecommendedMemoryBudgetMB(), 1024L * 1024L);
			autoRefreshMemoryLimits.set(true);
		}
		T_Log.log("Memory Limit: " + memoryLimitBytes + " : Auto Refresh: " + autoRefreshMemoryLimits); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Sets the maximum amount of memory for application to use.
	 *
	 */
	public static void setHostMemoryRefreshRateSec(long refreshRateSec) {
		if (refreshRateSec > -1) {
			refreshRate = refreshRateSec * 1000;// millis
		}
		T_Log.log("Refresh Rate: " + refreshRate); //$NON-NLS-1$
	}

	/*
	 * Sets the maximum amount of memory added to be reserved or overestimated for
	 * memory reservations and conservation.
	 *
	 */
	public static void setHostMemorySafetyMargin(double saftyMargin0to1Double) {
		if (Double.isFinite(saftyMargin0to1Double) && saftyMargin0to1Double >= 0.0 && saftyMargin0to1Double <= 1.0) {
			saftyMargin = saftyMargin0to1Double;
		}
		T_Log.log("Memory Margin: " + saftyMargin); //$NON-NLS-1$
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
		private final Map<Long, Long> trackedThreads = new ConcurrentHashMap<>();
		private final Map<Long, Process> trackedProcesses = new ConcurrentHashMap<>();
		private final Map<Long, Long> trackedProcessMaxUsage = new ConcurrentHashMap<>();
		private final AtomicLong totalAllocatedBytes = new AtomicLong(
				0);
		private final ScheduledExecutorService processMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(
					r,
					"TaskProcessMonitor"); //$NON-NLS-1$
			thread.setDaemon(true);
			return thread;
		});
		private final AtomicBoolean monitorStarted = new AtomicBoolean(
				false);
		private final AtomicBoolean closed = new AtomicBoolean(
				false);

		private static ThreadMXBean getThreadMXBean() {
			ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
			bean.setThreadAllocatedMemoryEnabled(true);
			return bean;
		}

		public void registerThread(Thread thread) {
			ThreadMXBean bean = getThreadMXBean();
			if (bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled()) {
				long threadId = thread.threadId();
				long allocated = bean.getThreadAllocatedBytes(threadId);
				if (allocated >= 0) {
					this.trackedThreads.putIfAbsent(Long.valueOf(threadId), Long.valueOf(allocated));
				}
			} else {
				T_Log.log("Error Thread Is Not Allocated Memory Supported or Thread Is Not Allocated Memory Enabled()"); //$NON-NLS-1$
			}
		}

		public synchronized void registerProcess(Process process) {
			Objects.requireNonNull(process, "process"); //$NON-NLS-1$
			if (this.closed.get()) {
				throw new IllegalStateException(
						"TaskHandle Is Already Closed"); //$NON-NLS-1$
			}
			long pid = process.pid();
			this.trackedProcesses.put(Long.valueOf(pid), process);
			// Remove the process when it exits.
			// Do NOT shut down the shared monitor here.
			process.onExit().whenComplete((_, _) -> {
				this.trackedProcesses.remove(Long.valueOf(pid), process);
			});
			// Start only one monitoring job per TaskHandle.
			if (this.monitorStarted.compareAndSet(false, true)) {
				long period = Math.max(1L, refreshRate / 2);
				try {
					this.processMonitor.scheduleWithFixedDelay(this::monitorProcesses, 0L, period, TimeUnit.MILLISECONDS);
				} catch (RuntimeException e) {
					this.monitorStarted.set(false);
					this.trackedProcesses.remove(Long.valueOf(pid), process);
					throw e;
				}
			}
		}

		private void monitorProcesses() {
			if (this.closed.get()) {
				return;
			}
			for (Map.Entry<Long, Process> entry : this.trackedProcesses.entrySet()) {
				try {
					long pid = entry.getKey().longValue();
					Process process = entry.getValue();
					if (!process.isAlive()) {
						this.trackedProcesses.remove(Long.valueOf(pid), process);
						continue;
					}
					long allocated = Translator.platformRuningOn == Platform.WINDOWS ? getProcessMemoryBytesWindows(
							pid) : Translator.platformRuningOn == Platform.LINUX ? getProcessMemoryBytesLinux(pid) : -1L;
					if (allocated >= 0) {
						this.trackedProcessMaxUsage.merge(Long.valueOf(pid), Long.valueOf(allocated), Math::max);
					}
				} catch (RuntimeException e) {
					T_Log.log("Error While Monitoring Task Process", e); //$NON-NLS-1$
				}
			}
		}

		private static long getProcessMemoryBytesWindows(long pid) {
			Process powershell = null;
			try {
				ProcessBuilder builder = new ProcessBuilder(
						"powershell.exe", //$NON-NLS-1$
						"-NoProfile", //$NON-NLS-1$
						"-NonInteractive", //$NON-NLS-1$
						"-Command", //$NON-NLS-1$
						"(Get-Process -Id " + pid + ").WorkingSet64"); //$NON-NLS-1$ //$NON-NLS-2$
				builder.redirectErrorStream(true);
				powershell = builder.start();
				// Never wait indefinitely for PowerShell.
				if (!powershell.waitFor(2, TimeUnit.SECONDS)) {
					powershell.destroyForcibly();
					powershell.waitFor(1, TimeUnit.SECONDS);
					return -1L;
				}
				if (powershell.exitValue() != 0) {
					return -1L;
				}
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(
								powershell.getInputStream()))) {
					String output = reader.readLine();
					return output == null ? -1L : Long.parseLong(output.trim());
				}
			} catch (InterruptedException e) {
				T_Log.log("Interupted At Process Monitor On Windows", e, true); //$NON-NLS-1$
				powershell.destroyForcibly();
				Thread.currentThread().interrupt();
				return -1L;
			} catch (IOException | NumberFormatException e) {
				T_Log.log("Error While Reading Process Memory Usage On Windows", e); //$NON-NLS-1$
				return -1L;
			} finally {
				if (powershell != null && powershell.isAlive()) {
					powershell.destroyForcibly();
				}
			}
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
				this.processMonitor.shutdownNow();
				this.trackedProcesses.clear();
				this.trackedThreads.clear();
				release(this.reservation);
			}
		}
	}

	/**
	 * Object For Updating Single Task Statistics.
	 */
	public static class TaskStatistics {
		private final AtomicLong executionCount = new AtomicLong();
		private final AtomicLong totalAllocatedBytes = new AtomicLong();
		private final AtomicLong maximumAllocatedBytes = new AtomicLong();
		private final AtomicLong minimumAllocatedBytes = new AtomicLong(
				Long.MAX_VALUE);
		private final AtomicLong averageAllocatedBytes = new AtomicLong();

		public void recordUsage(String taskName, long allocatedBytes) {
			this.totalAllocatedBytes.addAndGet(allocatedBytes);
			this.executionCount.incrementAndGet();
			this.maximumAllocatedBytes.accumulateAndGet(allocatedBytes, Math::max);
			this.minimumAllocatedBytes.accumulateAndGet(allocatedBytes, Math::min);
			this.averageAllocatedBytes.set(this.totalAllocatedBytes.get() / this.executionCount.get());
			if (this.totalAllocatedBytes.get() > memoryLimitBytes) {
				if (resourceHalvinglock.compareAndSet(false, true)) {
					try {
						if (this.totalAllocatedBytes.get() > memoryLimitBytes) {
							half(taskName);
						}
					} finally {
						T_Log.log(
								taskName + ": Total Executions: " + this.executionCount //$NON-NLS-1$
										.get());
						T_Log.log(
								taskName + ": Total Memory Usage: " + this.totalAllocatedBytes //$NON-NLS-1$
										.get());
						T_Log.log(taskName + ": Memory Usage Minimum: " + this.minimumAllocatedBytes.get()); //$NON-NLS-1$
						T_Log.log(taskName + ": Memory Usage Maximum: " + this.maximumAllocatedBytes.get()); //$NON-NLS-1$
						T_Log.log(taskName + ": Memory Usage Average: " + this.averageAllocatedBytes.get()); //$NON-NLS-1$
						resourceHalvinglock.set(false);
					}
				}
			}
		}

		/**
		 * Helper method to half the incermented and additive variabes
		 */
		private void half(String taskName) {
			this.executionCount.set(this.executionCount.get() / 2);
			this.totalAllocatedBytes.set(this.totalAllocatedBytes.get() / 2);
			T_Log.log(
					taskName + ": Memory Usage Variables Halved: Total Executions: " + this.executionCount //$NON-NLS-1$
							.get());
			T_Log.log(
					taskName + ": Memory Usage Variables Halved: Total Memory Usage: " + this.totalAllocatedBytes //$NON-NLS-1$
							.get());
			T_Log.log(taskName + ": Memory Usage Minimum: " + this.minimumAllocatedBytes.get()); //$NON-NLS-1$
			T_Log.log(taskName + ": Memory Usage Maximum: " + this.maximumAllocatedBytes.get()); //$NON-NLS-1$
			T_Log.log(taskName + ": Memory Usage Average: " + this.averageAllocatedBytes.get()); //$NON-NLS-1$
		}

		public long getAverageUsageBytes() {
			long count = this.executionCount.get();
			return count == 0 ? 0 : this.totalAllocatedBytes.get() / count;
		}

		public long getMaximumUsageBytes() {
			return this.maximumAllocatedBytes.get();
		}

		public long getMinimumUsageBytes() {
			long value = this.minimumAllocatedBytes.get();
			return value == Long.MAX_VALUE ? 0 : value;
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
	private static TaskHandle tryAcquire(String taskName, long requiredMemoryMB) throws Exception {
		if (autoRefreshMemoryLimits.get()) {
			refreshMemoryBudgetIfRequired();
		}
		long id = taskIds.incrementAndGet();
		if (id > 1000000000) {
			throw new IllegalStateException(
					"Task ID Space Exhausted"); //$NON-NLS-1$
		}
		long requiredBytes;
		try {
			requiredBytes = Math.multiplyExact(requiredMemoryMB, 1024L * 1024L);
		} catch (ArithmeticException _) {
			requiredBytes = Long.MAX_VALUE;
		}
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
		long now = System.currentTimeMillis();
		if (refreshRate > 0) {
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
		}
	}

	/**
	 * Record task's memory usage.
	 */
	private static void recordTaskUsage(String taskName, long allocatedBytes) {
		HostMemoryManagemant.taskStatistics.computeIfAbsent(taskName, _ -> new TaskStatistics())
				.recordUsage(taskName, allocatedBytes);
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
		long budget = (long) (available + (saftyMargin * available));
		T_Log.log("Recommended Memory Budget MB: " + budget); //$NON-NLS-1$
		// Keep safty margin available.
		return budget;
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

	@FunctionalInterface
	public interface TaskAction<T> {
		void execute(T taskHandle) throws Exception;
	}

	/**
	 * Execute a task if the needed memory is available and track its memory usage
	 * statistics.
	 */
	public static void execute(String taskName, final TaskAction<TaskHandle> action) throws Exception {
		execute(taskName, -1, action);
	}

	/**
	 * Execute a task if the needed memory is available and track its memory usage
	 * statistics.
	 * 
	 * @param <T>
	 */
	public static void execute(String taskName, long memoryAllocationOverride, final TaskAction<TaskHandle> action) throws Exception {
		long reserve = memoryAllocationOverride > -1 ? memoryAllocationOverride : getAverageTaskUsageMB(taskName);
		if (reserve > 0) {
			long remaining = memoryLimitBytes - reservedMemory.get();
			long margin = (long) (saftyMargin * reserve);
			reserve += Math.min(remaining, (reserve + margin));
		}
		T_Log.log("Requested Memory Reserve MB: " + reserve); //$NON-NLS-1$
		try (TaskHandle handle = tryAcquire(taskName, reserve)) {
			if (handle == null) {
				T_Log.log("Detected Memory Budget MB: " + getRecommendedMemoryBudgetMB()); //$NON-NLS-1$
				throw new IllegalStateException(
						"Insufficient Resources For Task: " + taskName); //$NON-NLS-1$
			}
			handle.registerThread(Thread.currentThread());
			try {
				action.execute(handle);
			} finally {
				handle.unregisterThread(Thread.currentThread());
				long allocatedBytes = handle.getTotalAllocatedBytes();
				recordTaskUsage(taskName, allocatedBytes);
				if (totalNetMemoryUsage.get() > memoryLimitBytes) {
					if (resourceHalvinglock.compareAndSet(false, true)) {
						try {
							if (totalNetMemoryUsage.get() > memoryLimitBytes) {
								half();
							}
						} finally {
							T_Log.log("Total Net Executed Tasks: " + totalCompletedTasks.get()); //$NON-NLS-1$
							T_Log.log("Total Net JVM Memory Usage: " + totalNetMemoryUsage.get()); //$NON-NLS-1$
							resourceHalvinglock.set(false);
						}
					}
				}
			}
		}
	}

	/**
	 * Helper method to half the incermented and additive variabes
	 */
	private static void half() {
		totalCompletedTasks.set(totalCompletedTasks.get() / 2);
		totalNetMemoryUsage.set(totalNetMemoryUsage.get() / 2);
		T_Log.log("Memory Usage Variables Halved: Total Net Executed Tasks: " + totalCompletedTasks.get()); //$NON-NLS-1$
		T_Log.log("Memory Usage Variables Halved: Total Net JVM Memory Usage: " + totalNetMemoryUsage.get()); //$NON-NLS-1$
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
		T_Log.log(sb.toString());
		return sb.toString();
	}
}
