import java.util.*;

import javax.lang.model.util.ElementScanner6;

public class Scheduler extends Thread {
	private Vector queue; // first priority
	private Vector queue1; // second priority
	private Vector queue2; // third priority
	private int timeSlice; // time slice for queue
	private int timeSlice1; // time slice for queue 1
	private int timeSlice2; // time slice for queue 2
	private static final int DEFAULT_TIME_SLICE = 1000;

	// New data added to p161 
	private boolean[] tids; // Indicate which ids have been used
	private static final int DEFAULT_MAX_THREADS = 10000;

	// A new feature added to p161 
	// Allocate an ID array, each element indicating if that id has been used
	private int nextId = 0;

	// initialize thread ID and 
	private void initTid(int maxThreads) {
		tids = new boolean[maxThreads];
		for (int i = 0; i < maxThreads; i++)
			tids[i] = false;
	}

	// A new feature added to p161 
	// Search an available thread ID and provide a new thread with this ID
	private int getNewTid() {
		for (int i = 0; i < tids.length; i++) {
			int tentative = (nextId + i) % tids.length;
			if (tids[tentative] == false) {
				tids[tentative] = true;
				nextId = (tentative + 1) % tids.length;
				return tentative;
			}
		}
		return -1;
	}

	// A new feature added to p161 
	// Return the thread ID and set the corresponding tids element to be unused
	private boolean returnTid(int tid) {
		if (tid >= 0 && tid < tids.length && tids[tid] == true) {
			tids[tid] = false;
			return true;
		}
		return false;
	}

	// A new feature added to p161 
	// Retrieve the current thread's TCB from all 3 queues 
	// to check if the TCB is in which queue to resume the process
	public TCB getMyTcb() {
		Thread myThread = Thread.currentThread(); // Get my thread object
		// synchronized all thread in queue 0 to find my thread
		synchronized (queue) {
			for (int i = 0; i < queue.size(); i++) {
				TCB tcb = (TCB) queue.elementAt(i);
				Thread thread = tcb.getThread();
				if (thread == myThread) // if this is my TCB, return it
					return tcb;
			}
		}
		// synchronized all thread in queue 1 to find my thread
		synchronized (queue1) {
			for (int i = 0; i < queue1.size(); i++) {
				TCB tcb = (TCB) queue1.elementAt(i);
				Thread thread = tcb.getThread();
				if (thread == myThread) // if this is my TCB, return it
					return tcb;
			}
		}
		// synchronized all thread in queue 2 to find my thread
		synchronized (queue2) {
			for (int i = 0; i < queue2.size(); i++) {
				TCB tcb = (TCB) queue2.elementAt(i);
				Thread thread = tcb.getThread();
				if (thread == myThread) // if this is my TCB, return it
					return tcb;
			}
		}
		return null;
	}

	// A new feature added to p161 
	// Return the maximal number of threads to be spawned in the system
	public int getMaxThreads() {
		return tids.length;
	}

	// default constructor
	public Scheduler() {
		timeSlice = DEFAULT_TIME_SLICE / 2; // for queue 0
		timeSlice1 = DEFAULT_TIME_SLICE; // for queue 1
		timeSlice2 = DEFAULT_TIME_SLICE * 2; // for queue 2
		queue = new Vector();
		queue1 = new Vector();
		queue2 = new Vector();
		initTid(DEFAULT_MAX_THREADS);
	}

	// constructor with quantum time specified
	public Scheduler(int quantum) {
		timeSlice = quantum / 2; // for queue 0
		timeSlice1 = quantum; // for queue 1
		timeSlice2 = quantum * 2; // for queue 2
		queue = new Vector();
		queue1 = new Vector();
		queue2 = new Vector();
		initTid(DEFAULT_MAX_THREADS);
	}

	// A new feature added to p161 
	// A constructor to receive the max number of threads to be spawned
	public Scheduler(int quantum, int maxThreads) {
		timeSlice = quantum / 2; // for queue 0
		timeSlice1 = quantum; // for queue 1
		timeSlice2 = quantum * 2; // for queue 2
		queue = new Vector();
		queue1 = new Vector();
		queue2 = new Vector();
		initTid(maxThreads);
	}

	// Make the schedule to sleep for 500ms or the same
	// time as quantum time specified
	private void schedulerSleep(int quantum_time) {
		try {
			Thread.sleep(quantum_time);
		} catch (InterruptedException e) {
			SysLib.cerr("Error! " + e + "\n");
		}
	}

	// A modified addThread of p161 example
	// only add thread to queue 0 in order to process
	// first with top priority
	public TCB addThread(Thread t) {
		TCB parentTcb = getMyTcb(); // get my TCB and find my TID
		int pid = (parentTcb != null) ? parentTcb.getTid() : -1;
		int tid = getNewTid(); // get a new TID
		if (tid == -1)
			return null;
		TCB tcb = new TCB(t, tid, pid); // create a new TCB
		queue.add(tcb);
		return tcb;
	}

	// A new feature added to p161
	// Removing the TCB of a terminating thread
	public boolean deleteThread() {
		TCB tcb = getMyTcb();
		if (tcb != null)
			return tcb.setTerminated();
		else
			return false;
	}

	// set sleep time in millisecond for thread
	public void sleepThread(int milliseconds) {
		try {
			sleep(milliseconds);
		} catch (InterruptedException e) {
			SysLib.cerr("Error! " + e + "\n");
		}
	}

	// function for processing threads in queue 0
	// if the thread doesn't finish its job in current
	// queue quantum time, push it to the back of queue 1 
	public void processQ0(Thread current) {
		// * * * queue 0 * * *
		while (queue.size() > 0) {
			TCB currentTCB = (TCB) queue.firstElement();
			// check if the TCB is terminated or not
			// if true, set this TCB ID to unuse
			if (currentTCB.getTerminated() == true) {
				queue.remove(currentTCB);
				returnTid(currentTCB.getTid());
				continue;
			}
			current = currentTCB.getThread(); // get thread from the TCB
			// start thread if it is not NULL
			if (current != null) {
				if (current.isAlive())
					current.resume();
				else
					current.start();
			}
			// put scheduler to sleep for the thread to process
			schedulerSleep(timeSlice);
			synchronized (queue) {
				// check if the thread is finished or not
				if (!current.isAlive()) {
					currentTCB.setTerminated();
					continue;
				}
				// suspend if the thread is yet finished
				if (current != null && current.isAlive())
					current.suspend();
				// move unfinished thread to queue 1
				queue.remove(currentTCB);
				queue1.add(currentTCB);
			}
		}
	}

	// function for processing threads in queue 1
	public void processQ1(Thread current) {
		// * * * queue 1 * * *
		while (queue1.size() > 0 && queue.isEmpty()) {
			TCB currentTCB = (TCB) queue1.firstElement();
			// check if the TCB is terminated or not
			// if true, set this TCB ID to unuse
			if (currentTCB.getTerminated() == true) {
				queue1.remove(currentTCB);
				returnTid(currentTCB.getTid());
				continue;
			}
			current = currentTCB.getThread(); // get thread from the TCB
			// check if the thread is currently suspended and resume
			// otherwise start thread with the new time quantum
			if (current != null) {
				if (current.isAlive())
					current.resume();
				else
					current.start();
			}
			// sleep for half of quantum time
			schedulerSleep(timeSlice);
			// check if queue 0 has any new thread
			// while processing this thread. If there is, suspend
			// current thread
			if (!queue.isEmpty()) {
				current.suspend();
				processQ0(current); // go back and process queue 0
				current.resume();
			}
			// sleep for the rest of this queue1 quantum time
			schedulerSleep(timeSlice1 - timeSlice);
			synchronized (queue1) {
				// check if the thread is finished or not
				if (!current.isAlive()) {
					currentTCB.setTerminated();
					continue;
				}
				// suspend if the thread is yet finished
				if (current != null && current.isAlive())
					current.suspend();
				// move unfinished thread to queue 1
				queue1.remove(currentTCB);
				queue2.add(currentTCB);
			}
		}
	}

	// function for processing threads in queue 2
	public void processQ2(Thread current) {
		// * * * queue 2 * * *
		while (queue2.size() != 0 && queue1.isEmpty() && queue.isEmpty()) {
			TCB currentTCB = (TCB) queue2.firstElement();
			// check if the TCB is terminated or not
			// if true, set this TCB ID to unuse
			if (currentTCB.getTerminated() == true) {
				queue2.remove(currentTCB);
				returnTid(currentTCB.getTid());
				continue;
			}
			current = currentTCB.getThread(); // get thread from the TCB
			// check if the thread is currently suspended and resume
			// otherwise start thread with the new time quantum
			if (current != null) {
				if (current.isAlive())
					current.resume();
				else 
					current.start();
			}
			// sleep for each 1/4 of the quantum time
			// and check high priority queue
			int time_remain = 0;
			for (int i = 0; i < timeSlice2; i += timeSlice) {
				schedulerSleep(timeSlice);
				time_remain += timeSlice;
				if (!queue1.isEmpty() || !queue.isEmpty()) {
					current.suspend();
					processQ0(current);
					processQ1(current);
					current.resume();

				}
			}
			// sleep for the rest of this queue quantum time
			schedulerSleep(timeSlice2 - time_remain);
			synchronized (queue2) {
				// check if the thread is finished or not
				if (!current.isAlive()) {
					currentTCB.setTerminated();
					continue;
				}
				// suspend if the thread is yet finished
				if (current != null && current.isAlive())
					current.suspend();
				// rotate unfinished thread back to 
				// the end of queue 2
				queue2.remove(currentTCB);
				queue2.add(currentTCB);
			}
		}
	}

	// A modified run of p161
	// this function will be initialized by the kernel
	public void run() {
		Thread current = null;
		while (true) {
			try {
				// get the next TCB and its thread
				if (queue.isEmpty() && queue1.isEmpty() && queue2.isEmpty())
					continue;
				else {
					processQ0(current);
					processQ1(current);
					processQ2(current);
				}
			} catch (NullPointerException e3) {
				SysLib.cerr("Error! " + e3 + "\n");
			}
		}
	}
}
