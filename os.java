import java.util.*;

//sos.siodisk(int JobNum); //start disk transfer
//sos.siodrum(int JobNum, int JobSize, int StartCoreAddr, int TransferDir); //start drum transfer (swap)
public class os {

	static HashMap<Integer, Job> jobTable;
	static int[] memory;
	static int lastTime;
	static int timeRan;
	static int currentlyRunning; //keeps track of which job is currently using CPU
	static int jobUsingIO;
	static Queue<Job> memoryQueue = new LinkedList<Job>();
	static int jobWaitingForMemory;
	static int jobWaitingForIO;
	static boolean runningIO;
	static boolean runningCPU;

    public static void startup() {
    	jobTable = new HashMap<Integer, Job>(); //allocate enough for 50 jobs
    	memory = new int [100]; //100k memory available in 1k blocks
    	runningCPU = false;
    	for (int i=0; i<100; i++) //initialize all memory to empty
    		memory[i] = 0;
    	lastTime = 0;
    	timeRan = 0;
    }

    //handles new incoming jobs
    public static void Crint (int[] a, int[] p){
    	System.out.println("crint");

    	lastTime = p[5];

    	//bookkeeping (interrupted, make sure info about job is not lost)

    	//p[1]: job number
    	//p[2]: job priority
    	//p[3]: job size (in kb)
    	//p[4]: maximum CPU time
    	//p[5]: current time
    	Job newJob = new Job(p[1], p[2], p[3], p[4], p[5], 0, 0); //create a new PCB with all the job data
    	jobTable.put(p[1], newJob); //put the new PCB into the job table

    	putInMemory(newJob); //put the new job in memory
    }

    //disk finished an I/O operation, I/O has been finished at top of I/O queue
    //p[5] is the only parameter
	public static void Dskint (int[] a, int[] p) {
		//bookkeeping (interrupted, make sure info about job is not lost)

		System.out.println("dskint");
		if (runningCPU == true) {
			timeRan = timeRan + (p[5] - lastTime);
		}

		lastTime = p[5];

		runningIO = false;

		Job job = jobTable.get(currentlyRunning);
	    a[0] = 2; //set to 2 to run CPU
	    p[2] = job.startAddress; //base address of job
	    p[3] = job.size; //size of job
	    p[4] = job.maxCPUtime - timeRan; //time slice

	    runningCPU = true;
	}

	//drum has finished swapping a job in or out of memory
	//p[5] is the only parameter and it is the current time
	public static void Drmint (int[] a, int[] p) {
		//bookkeeping (interrupted, make sure info about job is not lost)

		System.out.println("drmint");

		lastTime = p[5];

		//run the job
		Job job = jobTable.get(jobWaitingForMemory);
	    a[0] = 2; //set to 2 to run CPU
	    p[2] = job.startAddress; //base address of job
	    p[3] = job.size; //size of job
	    p[4] = job.maxCPUtime - timeRan; //time slice

	    runningCPU = true;
	    currentlyRunning = job.number;
	}

	//running job has run out of time
	//p[5] is the only parameter
	public static void Tro (int[] a, int[] p) {
		//bookkeeping (interrupted, make sure info about job is not lost)

		System.out.println("tro");
		if (runningCPU == true) {
			timeRan = timeRan + (p[5] - lastTime);
		}

		lastTime = p[5];

		//run on CPU again
		Job job = jobTable.get(currentlyRunning);
	    a[0] = 2; //set to 2 to run CPU
	    p[2] = job.startAddress; //base address of job
	    p[3] = job.size; //size of job
	    p[4] = job.maxCPUtime - timeRan; //time slice

	    runningCPU = true;
	}

	//running job wants service
	public static void Svc (int[] a, int[] p) {
		//bookkeeping (interrupted, make sure info about job is not lost)

		if (runningCPU == true) {
			timeRan = timeRan + (p[5] - lastTime);
		}

		lastTime = p[5];

		if (a[0] == 5) { //job wants to terminate
			System.out.println("job wants terminate");

			//stop the currently running job
			Job job = jobTable.get(currentlyRunning);
			a[0] = 1;
			runningCPU = false;

			timeRan = 0;
		}
		else if (a[0] == 6) { //job is requesting another disk I/O
			System.out.println("job requesting another disk I/O");

			runningIO = true;
			sos.siodisk(currentlyRunning);

			//run on CPU again
			Job job = jobTable.get(currentlyRunning);
		    a[0] = 2; //set to 2 to run CPU
		    p[2] = job.startAddress; //base address of job
		    p[3] = job.size; //size of job
		    p[4] = job.maxCPUtime - timeRan; //time slice

		    runningCPU = true;
		}
		else if (a[0] == 7) { //job is requesting to be blocked
			System.out.println("job requesting to be blocked");

			//stop the currently running job
			Job job = jobTable.get(currentlyRunning);
			a[0] = 1;
			runningCPU = false;

			//set blocked flag?

			if (runningIO == false) { //run only if IO is finished
				//run on CPU again
			    a[0] = 2; //set to 2 to run CPU
			    p[2] = job.startAddress; //base address of job
			    p[3] = job.size; //size of job
			    p[4] = job.maxCPUtime - timeRan; //time slice

			    runningCPU = true;
			}
		}
	}

	public static void putInMemory(Job job) {
		int start = 0; //starting address
		int count = 0;
		for (int i=0; i<100; i++) {
			if (memory[i] == 0) {
				count++;
			}
			else {
				start = i+1;
				count = 0;
			}
			if (job.size == count) {
				job.state = "ready";
				job.startAddress = start;
				jobWaitingForMemory = job.number;
				System.out.println("address: " + job.startAddress + " size: " + job.size); //debug
				sos.siodrum(job.number, job.size, start, 0);
				break;
			}
		}
	}
}