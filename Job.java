public class Job {
	int number; //job number
	int priority; //priority of the job
	int size; //size of the job
	int maxCPUtime; //maximum CPU time allowed for job
	int currentTime; //
	int startAddress; //starting address of the job
	int lastTime; //to keep track of the last time it was interrupted
	int timeRan; //amount of time ran on CPU
	//String state;
	boolean runningIO;
	boolean blocked;
	boolean inCore;
	boolean killBit; //terminate after finishing IO

	public Job(int number, int priority, int size, int maxCPUtime, int lastTime) {
		this.number = number;
		this.priority = priority;
		this.size = size;
		this.maxCPUtime = maxCPUtime;
		this.currentTime = currentTime;
		this.startAddress = startAddress;
		this.lastTime = lastTime;
		this.timeRan = 0;
		//this.state = "created";
		this.runningIO = false;
		this.blocked = false;
		this.inCore = false;
		this.killBit = false;
	}
}