/* SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (c) 2008-2012 Philippe Fournier-Viger
 * This file is derived from MemoryLogger.java of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * Modified in 2026 for TKCM: memory units, formatting and peak-pool reporting.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program (file LICENSE). If not, see <http://www.gnu.org/licenses/>.
 *
 * Do not remove copyright and license information.
 */

/**
 * This class is used to record the maximum memory usaged of an algorithm during
 * a given execution.
 * It is implemented by using the "singleton" design pattern.
 *
 */
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

public class MemoryLogger {

	// the only instance  of this class (this is the "singleton" design pattern)
	private static MemoryLogger instance = new MemoryLogger();

	// variable to store the maximum memory usage
	private double maxMemory = 0;

	// Memory unit constants (in bytes)
	public static final long BYTES_PER_KB = 1024L;
	public static final long BYTES_PER_MB = 1024L * 1024L;
	public static final long BYTES_PER_GB = 1024L * 1024L * 1024L;
	public static final long BYTES_PER_TB = 1024L * 1024L * 1024L * 1024L;

	/**
	 * Memory unit enumeration
	 */
	public enum MemoryUnit {
		BYTES, KB, MB, GB, TB
	}

	/**
	 * Method to obtain the only instance of this class
	 * @return instance of MemoryLogger
	 */
	public static MemoryLogger getInstance(){
		return instance;
	}

	/**
	 * To get the maximum amount of memory used until now
	 * @return a double value indicating memory as megabytes
	 */
	public double getMaxMemory() {
		return maxMemory;
	}

	/**
	 * Reset the maximum amount of memory recorded.
	 */
	public void reset(){
		maxMemory = 0;
	}

	/**
	 * Check the current memory usage and record it if it is higher
	 * than the amount of memory previously recorded.
	 */
	public void checkMemory() {
		double currentMemory = (Runtime.getRuntime().totalMemory() -  Runtime.getRuntime().freeMemory())
				/ 1024d / 1024d;
		if (currentMemory > maxMemory) {
			maxMemory = currentMemory;
		}
	}

	/**
	 * Get peak heap memory usage across all memory pools
	 * @return Peak heap usage in megabytes
	 */
	public static double getPeakHeapUsage() {
		double retVal = 0;
		try {
			List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
			double total = 0;
			for (MemoryPoolMXBean memoryPoolMXBean : pools) {
				if (memoryPoolMXBean.getType() == MemoryType.HEAP) {
					long peakUsed = memoryPoolMXBean.getPeakUsage().getUsed();
					total = total + peakUsed;
				}
			}
			retVal = total / 1024 / 1024;
		} catch (Throwable t) {
			System.err.println("Exception in memory monitoring: " + t);
		}
		return retVal;
	}

	/**
	 * Get peak heap memory usage in specified unit
	 * @param unit Memory unit (BYTES, KB, MB, GB, TB)
	 * @return Peak heap usage in specified unit
	 */
	public static double getPeakHeapUsage(MemoryUnit unit) {
		double retVal = 0;
		try {
			List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
			long total = 0;
			for (MemoryPoolMXBean memoryPoolMXBean : pools) {
				if (memoryPoolMXBean.getType() == MemoryType.HEAP) {
					long peakUsed = memoryPoolMXBean.getPeakUsage().getUsed();
					total = total + peakUsed;
				}
			}
			retVal = convertBytes(total, unit);
		} catch (Throwable t) {
			System.err.println("Exception in memory monitoring: " + t);
		}
		return retVal;
	}

	/**
	 * Get current memory usage in bytes
	 * @return Current memory usage in bytes
	 */
	public static long getCurrentMemoryBytes() {
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	/**
	 * Get current memory usage in specified unit
	 * @param unit Memory unit (BYTES, KB, MB, GB, TB)
	 * @return Current memory usage in specified unit
	 */
	public static double getCurrentMemory(MemoryUnit unit) {
		long bytes = getCurrentMemoryBytes();
		return convertBytes(bytes, unit);
	}

	/**
	 * Convert bytes to specified memory unit
	 * @param bytes Number of bytes
	 * @param unit Target memory unit
	 * @return Converted value
	 */
	public static double convertBytes(long bytes, MemoryUnit unit) {
		switch (unit) {
			case BYTES: return bytes;
			case KB:    return bytes / (double) BYTES_PER_KB;
			case MB:    return bytes / (double) BYTES_PER_MB;
			case GB:    return bytes / (double) BYTES_PER_GB;
			case TB:    return bytes / (double) BYTES_PER_TB;
			default:    return bytes;
		}
	}

	/**
	 * Format bytes as human-readable string with automatic unit selection
	 * @param bytes Number of bytes
	 * @return Formatted string (e.g., "1.5 MB", "2.3 GB")
	 */
	public static String formatBytes(long bytes) {
		if (bytes < BYTES_PER_KB) {
			return bytes + " B";
		} else if (bytes < BYTES_PER_MB) {
			return String.format("%.2f KB", bytes / (double) BYTES_PER_KB);
		} else if (bytes < BYTES_PER_GB) {
			return String.format("%.2f MB", bytes / (double) BYTES_PER_MB);
		} else if (bytes < BYTES_PER_TB) {
			return String.format("%.2f GB", bytes / (double) BYTES_PER_GB);
		} else {
			return String.format("%.2f TB", bytes / (double) BYTES_PER_TB);
		}
	}

	/**
	 * Format memory value with specified unit
	 * @param value Memory value
	 * @param unit Memory unit
	 * @return Formatted string (e.g., "1.50 MB", "2.30 GB")
	 */
	public static String formatMemory(double value, MemoryUnit unit) {
		String unitStr;
		switch (unit) {
			case BYTES: unitStr = "B"; break;
			case KB:    unitStr = "KB"; break;
			case MB:    unitStr = "MB"; break;
			case GB:    unitStr = "GB"; break;
			case TB:    unitStr = "TB"; break;
			default:    unitStr = "B"; break;
		}

		if (unit == MemoryUnit.BYTES) {
			return String.format("%.0f %s", value, unitStr);
		} else {
			return String.format("%.2f %s", value, unitStr);
		}
	}

	/**
	 * Format current memory usage as human-readable string
	 * @return Formatted string with automatic unit
	 */
	public static String formatCurrentMemory() {
		return formatBytes(getCurrentMemoryBytes());
	}

	/**
	 * Format peak heap usage as human-readable string
	 * @return Formatted string with automatic unit
	 */
	public static String formatPeakHeapUsage() {
		long bytes = (long) (getPeakHeapUsage() * BYTES_PER_MB);
		return formatBytes(bytes);
	}

	/**
	 * Get memory statistics as a formatted string
	 * @return Multi-line string with memory statistics
	 */
	public static String getMemoryStats() {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		StringBuilder sb = new StringBuilder();
		sb.append("Memory Statistics:\n");
		sb.append("  Used Memory : ").append(formatBytes(usedMemory)).append("\n");
		sb.append("  Free Memory : ").append(formatBytes(freeMemory)).append("\n");
		sb.append("  Total Memory: ").append(formatBytes(totalMemory)).append("\n");
		sb.append("  Max Memory  : ").append(formatBytes(maxMemory)).append("\n");
		sb.append("  Peak Heap   : ").append(formatPeakHeapUsage());

		return sb.toString();
	}
}
