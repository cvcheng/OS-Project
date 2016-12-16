import java.util.*;

public class os {

	static HashMap<Integer, Job> jobTable;
	static int[] memory; //the free space table
	static int jobWaitingForMemory;
	static HashMap<Integer, Job> jobQueue; //jobs waiting for main memory
	static Job runningJob; //pointer to the running job
	static int currentlyRunning;
	static Queue<Job> CPU_Queue; //jobs waiting to use CPU
	static Queue<Job> IO_Queue; //jobs waiting to use IO

    public static void startup() {
    	jobTable = new HashMap<Integer, Job>();
    	memory = new int [100]; //allocate 100k memory available in 1k blocks
    	for (int i=0; i<100; i++) //initialize all memory to empty
    		memory[i] = 0;
    	jobWaitingForMemory = 0;
    	currentlyRunning = 0;
    	CPU_Queue = new LinkedList<Job>();
    	IO_Queue = new LinkedList<Job>();
    	jobQueue = new HashMap<Integer, Job>();
    	runningJob = null;
    	//sos.ontrace();
    }

    //handles new incoming jobs
    public static void Crint (int[] a, int[] p){
    	//if (p[1] == 8)
    		//return;

    	BookKeep(p[5]);

    	System.out.println("crint");

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

		//jobWaitingForMemory.inCore = true; //set the inCore flag to true
		Job currentJob = jobQueue.remove(jobWaitingForMemory); //remove job that finished being placed in memory from job queue
		jobWaitingForMemory = 0; //finished putting selected job into memory

		CPU_Queue.add(currentJob); //place it into CPU queue

		swapper(); //let a new job into memory

		runCPU(a,p);
	}

    //disk finished an I/O operation, I/O has been finished at top of I/O queue
    //p[5] is the only parameter
	public static void Dskint (int[] a, int[] p) {
		System.out.println("dskint");

		BookKeep(p[5]);

		Job firstJob = IO_Queue.remove(); //remove the first job from IO queue since it's finished running IO
		firstJob.runningIO = false; //set the runningIO flag of the job removed to false

		if (firstJob.blocked == true) {
			CPU_Queue.add(firstJob); //add it back to queue to run CPU again
			firstJob.blocked = false; //set blocked to false since job finished IO
		}

		//run the next job on the IO queue if it isn't empty
		if (!IO_Queue.isEmpty())
			sos.siodisk(IO_Queue.peek().number);

		runCPU(a,p);
	}

	//running job has run out of time, terminate if it reached its maxCPUtime
	//p[5] is the only parameter
	public static void Tro (int[] a, int[] p) {
		BookKeep(p[5]);

		System.out.println("tro");

		if (runningJob.timeRan == runningJob.maxCPUtime) { //check if job has reached its max CPU time
			a[0] = 1; //stop CPU
			CPU_Queue.remove(); //remove job from CPU queue

			//remove it from memory table
			for (int i=runningJob.startAddress; i<runningJob.size; i++)
				memory[i] = 0;

			runningJob =  null;
		}

		runCPU(a,p);
	}

	//running job wants service
	public static void Svc (int[] a, int[] p) {
		BookKeep(p[5]);

		if (a[0] == 5) { //job wants to terminate
			System.out.println("job " + runningJob.number + " wants terminate");

			//remove program from memory
			for (int i=runningJob.startAddress; i<runningJob.size; i++)
				memory[i] = 0;

			//remove job from CPU queue
			CPU_Queue.remove();

			//stop the currently running job
			a[0] = 1;
			runningJob = null;

			runCPU(a,p);
		}
		else if (a[0] == 6) { //job is requesting another disk I/O
			System.out.println("job requesting another disk I/O");

			IO_Queue.add(runningJob);

			if (runningJob.blocked && !CPU_Queue.isEmpty())
				CPU_Queue.remove(); //remove from CPU queue

			//if first job in IO queue isn't running IO, let it run IO
			if (IO_Queue.peek().runningIO == false) {
				sos.siodisk(IO_Queue.peek().number);
				IO_Queue.peek().runningIO = true;
			}

			runCPU(a,p);
		}
		else if (a[0] == 7) { //job is requesting to be blocked
			System.out.println("job requesting to be blocked");

			if (runningJob.runningIO == true) { //stop running job if its IO is not finished
				a[0] = 1;
				runningJob.blocked = true;
				runningJob = null;

				if (!CPU_Queue.isEmpty())
					CPU_Queue.remove(); //remove from CPU queue
			}

			runCPU(a,p);
		}
	}

	public static void swapper() {
		int CPUtime = 99999; //set to a really high number
		Job selectedJob = null; //the job to swap in

		//check if there are any jobs waiting for memory and select the job with the least CPU time
		for (int jobNum: jobQueue.keySet()) {
			Job currentJob = jobQueue.get(jobNum);
			if (!currentJob.inCore) {
				if (currentJob.maxCPUtime < CPUtime) {
					CPUtime = currentJob.maxCPUtime;
					selectedJob = currentJob;
				}
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
				for (int j=0; j<100; j++)
					System.out.print(memory[j] + " ");
				System.out.println("");
				sos.siodrum(selectedJob.number, selectedJob.size, start, 0);
				break;
			}
		}
	}

	public static void BookKeep(int currentTime)
	{
		System.out.print("CPU Queue: ");
		for(Job object : CPU_Queue) {
		    System.out.print(object.number);
		}
		System.out.print(" ");

		System.out.print("IO Queue: ");
		for(Job object : IO_Queue) {
		    System.out.print(object.number);
		}
		System.out.print(" ");
		if (runningJob != null)
			System.out.print("Running Job: " + runningJob.number);
		System.out.println("");

		if (runningJob != null) {
			runningJob.timeRan += (currentTime - runningJob.lastTime);
			runningJob.lastTime = currentTime;
		}
	}

	public static void runCPU(int[] a, int[] p)
    {
		/*if (currentlyRunning != 0) { //check if there was a job already running before being interrupted
			//run the job again
		    a[0] = 2; //set to 2 to run CPU
		    p[2] = runningJob.startAddress; //base address of job
		    p[3] = runningJob.size; //size of job
		    p[4] = runningJob.maxCPUtime - runningJob.timeRan; //time slice
		}
		else {
			//search for a job that isn't running IO, unblocked, and in core
	    	for(Job job: CPU_Queue) {
			    if (!job.runningIO && !job.blocked && job.inCore) {
				    //run the job
				    a[0] = 2; //set to 2 to run CPU
				    p[2] = job.startAddress; //base address of job
				    p[3] = job.size; //size of job
				    p[4] = job.maxCPUtime - job.timeRan; //time slice
				    job.lastTime = p[5]; //set the current time for the job
			    	currentlyRunning = job.number; //set currentlyRunning to the job number
			    	runningJob = job;
			    	break;
			    }
			}
		}*/	
	   	for(Job job: CPU_Queue) {
		    if (!job.blocked) {
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
    }

}