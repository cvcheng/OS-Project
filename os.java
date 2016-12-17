import java.util.*;

public class os {

	//static HashMap<Integer, Job> jobTable;
	static int[] memory; //the free space table
	static int jobWaitingForMemory;
	static HashMap<Integer, Job> jobQueue; //jobs waiting for main memory
	static Job runningJob; //pointer to the running job
	static int currentlyRunning;
	static int currentlyRunningIO;
	static HashMap<Integer, Job> CPU_Queue; //jobs waiting to use CPU
	static HashMap<Integer, Job> IO_Queue; //jobs waiting to use IO
	static boolean drumBusy;

    public static void startup() {
    	//jobTable = new HashMap<Integer,Job>();
    	memory = new int [100]; //allocate 100k memory available in 1k blocks
    	for (int i=0; i<100; i++) //initialize all memory to empty
    		memory[i] = 0;
    	jobWaitingForMemory = 0;
    	currentlyRunning = 0;
    	CPU_Queue = new HashMap<Integer, Job>();
    	IO_Queue = new HashMap<Integer, Job>();
    	jobQueue = new HashMap<Integer, Job>();
    	runningJob = null;
    	drumBusy = false;
    	currentlyRunningIO = 0;
    	//sos.ontrace();
    }

    //handles new incoming jobs
    public static void Crint (int[] a, int[] p){
    	//if (p[1] == 2)
    		//return;

    	BookKeep(p[5]);

    	System.out.println("crint: job " + p[1] + " entered, maxCPUtime = " + p[4]);

    	Job newJob = new Job(p[1], p[2], p[3], p[4], p[5]); //create a new Job object with all the job data
    	jobQueue.put(p[1], newJob); //put the new job into the job queue

    	if (jobWaitingForMemory == 0)
    		swapper(); //put a new job into memory

    	runCPU(a,p);
    }

    //drum has finished swapping a job in or out of memory
	//p[5] is the only parameter and it is the current time
	public static void Drmint (int[] a, int[] p) {
		BookKeep(p[5]);

		System.out.println("drmint");
		drumBusy = false;

		//jobWaitingForMemory.inCore = true; //set the inCore flag to true
		Job currentJob = jobQueue.remove(jobWaitingForMemory); //remove job that finished being placed in memory from job queue
		jobWaitingForMemory = 0; //finished putting selected job into memory

		CPU_Queue.put(currentJob.number, currentJob); //place it into CPU queue


		swapper(); //let a new job into memory

		runCPU(a,p);
	}

    //disk finished an I/O operation, I/O has been finished at top of I/O queue
    //p[5] is the only parameter
	public static void Dskint (int[] a, int[] p) {
		System.out.println("dskint");
		
		BookKeep(p[5]);

		Job firstJob = IO_Queue.remove(currentlyRunningIO); //remove the first job from IO queue since it's finished running IO
		firstJob.runningIO = false; //set the runningIO flag of the job removed to false
		currentlyRunningIO = 0;

		if (firstJob.blocked == true) {
			CPU_Queue.put(firstJob.number, firstJob); //add it back to queue to run CPU again
			firstJob.blocked = false; //set blocked to false since job finished IO
		}

		//remove job from memory if job's killbit is true
		if (firstJob.killBit == true) {
			for (int i=firstJob.startAddress; i<firstJob.startAddress+firstJob.size; i++)
				memory[i] = 0;
		}

		//run the next job on the IO queue if it isn't empty
		for (int jobNum: IO_Queue.keySet()) {
			sos.siodisk(IO_Queue.get(jobNum).number);
			currentlyRunningIO = jobNum;
			break;
		}

		runCPU(a,p);
	}

	//running job has run out of time, terminate if it reached its maxCPUtime
	//p[5] is the only parameter
	public static void Tro (int[] a, int[] p) {
		BookKeep(p[5]);

		System.out.println("tro");

		if (runningJob.timeRan == runningJob.maxCPUtime) { //check if job has reached its max CPU time
			a[0] = 1; //stop CPU
			CPU_Queue.remove(runningJob.number); //remove job from CPU queue

			//remove job from memory if job isn't still doing IO
			if (!runningJob.runningIO) {
				for (int i=runningJob.startAddress; i<runningJob.startAddress+runningJob.size; i++)
					memory[i] = 0;
			}
			else
				runningJob.killBit = true; //set killbit to true to remove from memory when it finishes IO

			runningJob =  null;

			swapper();
		}

		runCPU(a,p);
	}

	//running job wants service
	public static void Svc (int[] a, int[] p) {
		BookKeep(p[5]);

		if (a[0] == 5) { //job wants to terminate
			//remove job from memory if job isn't still doing IO
			if (!runningJob.runningIO) {
				for (int i=runningJob.startAddress; i<runningJob.startAddress+runningJob.size; i++)
					memory[i] = 0;
			}
			else
				runningJob.killBit = true;

			//remove job from CPU queue
			CPU_Queue.remove(runningJob.number);

			//stop the currently running job
			a[0] = 1;
			runningJob = null;

			swapper();

			runCPU(a,p);
		}
		else if (a[0] == 6) { //job is requesting another disk I/O
			System.out.println("job requesting another disk I/O");

			IO_Queue.put(runningJob.number, runningJob); //add to IO queue

			//if the job is blocked and in the CPU queue, remove it from CPU queue
			if (runningJob.blocked && !CPU_Queue.isEmpty())
				CPU_Queue.remove(runningJob.number);

			//if first job in IO queue isn't running IO, let it run IO
			//this check is to prevent using the disk if it's busy
			if (currentlyRunningIO == 0) {
				for (int jobNum: IO_Queue.keySet()) {
					sos.siodisk(jobNum);
					currentlyRunningIO = runningJob.number;
					IO_Queue.get(jobNum).runningIO = true;
					break;
				}
			}

			runningJob.runningIO = true;

			runCPU(a,p);
		}
		else if (a[0] == 7) { //job is requesting to be blocked
			System.out.println("job requesting to be blocked");

			if (runningJob.runningIO == true) { //stop running job if its IO is not finished
				if (!CPU_Queue.isEmpty())
					CPU_Queue.remove(runningJob.number); //remove from CPU queue

				a[0] = 1;
				runningJob.blocked = true;
				runningJob = null;
			}

			runCPU(a,p);
		}
	}

	public static void swapper() {
		if (drumBusy)
			return;

		int CPUtime = 99999; //set to a really high number
		Job selectedJob = null; //the job to swap in

		//check if there are any jobs waiting for memory and select the job with the least CPU time
		for (int jobNum: jobQueue.keySet()) {
			Job currentJob = jobQueue.get(jobNum);
			if (currentJob.maxCPUtime < CPUtime) {
				CPUtime = currentJob.maxCPUtime;
				selectedJob = currentJob;
			}
		}

		if (selectedJob == null) //no jobs to swap in
			return;

		int start = 0; //starting address
		int count = 0; //keeps a count of how many contiguous blocks of memory
		for (int i=0; i<100; i++) {
			//checks if there is enough free memory available for the job
			if (memory[i] == 0) {
				count++;
			}
			else {
				start = i+1;
				count = 0;
			}
			//if there is enough memory, allocate it for the job
			if (selectedJob.size == count) {
				selectedJob.startAddress = start;
				jobWaitingForMemory = selectedJob.number;
				System.out.println("Selected Job: " + selectedJob.number);
				System.out.println("address: " + selectedJob.startAddress + " size: " + selectedJob.size); //debug
				for (int j=start; j<count+start; j++)
					memory[j] = selectedJob.number;
				/*for (int j=0; j<100; j++)
					System.out.print(memory[j] + " ");
				System.out.println("");*/
				System.out.println("");
				drumBusy = true;
				sos.siodrum(selectedJob.number, selectedJob.size, start, 0);
				break;
			}
		}
	}

	public static void BookKeep(int currentTime)
	{
		if (runningJob != null) {
			runningJob.timeRan += (currentTime - runningJob.lastTime);
			runningJob.lastTime = currentTime;
		}
	}

	public static void runCPU(int[] a, int[] p)
    {
    	System.out.print("CPU_Queue: ");
		for(int jobNum: CPU_Queue.keySet()) {
		    System.out.print(jobNum + " ");
		}

		System.out.print("IO_Queue: ");
		for(int jobNum: IO_Queue.keySet()) {
		    System.out.print(jobNum + " ");
		}

		if (runningJob != null)
			System.out.print("Running Job: " + runningJob.number);
		System.out.println(" RunningIO: " + currentlyRunningIO);

		boolean foundJob = false;
	   	for (int jobNum: CPU_Queue.keySet()) {
			Job job = CPU_Queue.get(jobNum);
		    if (!job.blocked) {
		    	foundJob = true;
			    //run the job
			    a[0] = 2; //set to 2 to run CPU
			    p[2] = job.startAddress; //base address of job
			    p[3] = job.size; //size of job
			    p[4] = job.maxCPUtime - job.timeRan; //time slice
			    job.lastTime = p[5]; //set the current time for the job
		    	runningJob = job;
		    	break;
		    }
		}
		if (!foundJob)
			a[0] = 1;
    }

}