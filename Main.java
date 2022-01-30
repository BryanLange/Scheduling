import java.io.*;
import java.util.Scanner;

public class Main {
	
	public static void main(String[] args) {
		if(args.length == 5) {		
			// display file names on console
			for(int i=0; i<args.length; i++) {
				System.out.println("args["+ i +"] : " + args[i]);
			}
			
			
			// open input and output files
			Scanner inFile1 = null, inFile2 = null;
			PrintWriter outFile1 = null, outFile2 = null;
			try {
				inFile1 = new Scanner(new File(args[0]));
				inFile2 = new Scanner(new File(args[1]));
				outFile1 = new PrintWriter(new File(args[3]));
				outFile2 = new PrintWriter(new File(args[4]));
			} catch(FileNotFoundException e) {
				System.out.println("A file was not found, Program terminated.");
				System.exit(0);
			}
					
			
			// initialization
			int numProcs = Integer.parseInt(args[2]);
			Scheduling x = new Scheduling(inFile1, inFile2, numProcs);
			
			
			
			while(!x.graphIsEmpty()) {
				
				x.loadOpen(); // builds the "open" list
				
				x.printList(outFile2); // print "open" list to debug file
				
				x.loadProcAry(); // place jobs on processors
				
				
				// check if there is a cycle in the graph
				boolean hasCycle = x.checkCycle();
				if(hasCycle) {
					outFile1.println("There is a cycle in the graph! Program terminated.");
					System.out.print("There is a cycle in the graph! Program terminated.");
					inFile1.close(); inFile2.close();
					outFile1.close(); outFile2.close();
					System.exit(0);
				}
				
				x.printScheduleTable(outFile1);
				
				x.incrementCurrentTime();
				
				x.updateProcTime(); // decrement all processors by 1
				
				
				// find all done processors
				// remove done jobs and their outgoing edges from graph
				int jobId;
				do {
					jobId = x.findDoneProc();
					if(jobId > 0) {
						x.removeJobFromGraph(jobId);
						x.deleteEdge(jobId);
					}
				} while(jobId > 0);
			}
				
			// final print of schedule table
			x.printScheduleTable(outFile1);
			
			
			// close input and output files
			inFile1.close();
			inFile2.close();
			outFile1.close();
			outFile2.close();
		}
		else {
			System.out.println("Invalid number of arguments.");
		}
	} // end main
	
	
	
	public static class Scheduling {
		int numNodes;
		int numProcs;
		int procUsed;
		Jobs[] jobAry;
		Proc[] procAry;
		Node open;
		int[][] adjMatrix;
		int[] parentCountAry;
		int[] dependentCountAry;
		int[] onGraphAry;
		int totalJobTimes;
		int[][] scheduleTable;
		int currentTime;
		
		
		// constructor
		public Scheduling(Scanner inFile1, Scanner inFile2, int nProcs) {
			numProcs = nProcs;
			initialization(inFile1, inFile2);
			procUsed = 0;		
			open = null;
			currentTime = 0;
		} // end constructor
		
		
		// dynamically allocate arrays, load adjacency matrix from inFile1
		// compute parent count, dependency count, and totalJobTimes
		public void initialization(Scanner inFile1, Scanner inFile2) {
			numNodes = inFile1.nextInt();
			
			if(numProcs <= 0) {
				System.out.println("Need 1 or more processors, program terminated.");
				System.exit(0);
			} else if(numProcs > numNodes) {
				numProcs = numNodes;
			}
			
			adjMatrix = new int[numNodes+1][numNodes+1];
			for(int i=0; i<numNodes+1; i++) {
				for(int j=0; j<numNodes+1; j++) {
					adjMatrix[i][j] = 0;
				}
			}
			
			jobAry = new Jobs[numNodes+1];
			parentCountAry = new int[numNodes+1];
			dependentCountAry = new int[numNodes+1];
			onGraphAry = new int[numNodes+1];
			for(int i=0; i<=numNodes; i++) {
				jobAry[i] = new Jobs();
				parentCountAry[i] = 0;
				dependentCountAry[i] = 0;
				onGraphAry[i] = 1;
			}
			
			procAry = new Proc[numProcs+1];	
			for(int i=0; i<=numProcs; i++) {
				procAry[i] = new Proc();
			}
			
			loadMatrix(inFile1);
			
			computeParentCount();
			
			computeDependentCount();
			
			totalJobTimes = constructJobAry(inFile2);
			
			scheduleTable = new int[numProcs+1][totalJobTimes+1];
			for(int i=0; i<=numProcs; i++) {
				for(int j=0; j<=totalJobTimes; j++) {
					scheduleTable[i][j] = 0;
				}
			}
		} // end initialization()
		
		
		// loads adjacency matrix from given inFile
		public void loadMatrix(Scanner inFile1) {
			while(inFile1.hasNext()) {
				int x = inFile1.nextInt();
				int y = inFile1.nextInt();
				adjMatrix[x][y] = 1;
			}
		} // end loadMatrix()
		
		
		// computes the number of parents for each node in the graph
		public void computeParentCount() {
			for(int nodeId=1; nodeId<=numNodes; nodeId++) {
				for(int i=1; i<=numNodes; i++) {
					parentCountAry[nodeId] += adjMatrix[i][nodeId];
				}
			}
		} // end computeParentCount()
		
		
		// computes the number of dependecies for each node in the graph
		public void computeDependentCount() {
			for(int nodeId=1; nodeId<=numNodes; nodeId++) {
				for(int i=1; i<=numNodes; i++) {
					dependentCountAry[nodeId] += adjMatrix[nodeId][i];
				}
			}
		} // end computeDependentCount()
		
		
		// constructs jobAry[] from inFile2, returns total job time
		public int constructJobAry(Scanner inFile2) {
			int nNodes = inFile2.nextInt();
			int totalTime = 0;
				
			while(inFile2.hasNext()) {
				int nodeId = inFile2.nextInt();
				int jobTime = inFile2.nextInt();
				totalTime += jobTime;
				
				jobAry[nodeId].jobTime = jobTime;
				jobAry[nodeId].onWhichProc = -1;
				jobAry[nodeId].onOpen = 0;
				jobAry[nodeId].parentCount = parentCountAry[nodeId];
				jobAry[nodeId].dependentCount = dependentCountAry[nodeId];
			}		
			return totalTime;
		} // end constructJobAry()
		
		
		// builds the "open" list with all available orphan nodes
		public void loadOpen() {
			int orphanNode;
			do {
				orphanNode = findOrphan();
				
				if(orphanNode > 0) {
					int jobId = orphanNode;
					int jobTime = jobAry[jobId].jobTime;	
					Node newNode = new Node(jobId, jobTime, dependentCountAry[jobId]);
					
					openInsert(newNode);
					jobAry[jobId].onOpen = 1;
				}
			} while(orphanNode != -1);
		} // end loadOpen()
		
		
		// returns the next orphan node
		public int findOrphan() {
			for(int i=1; i<=numNodes; i++) {
				if(jobAry[i].parentCount <= 0 && jobAry[i].onOpen == 0 
						&& jobAry[i].onWhichProc <= 0) {
					return i;
				}
			}
			return -1;
		} // end findOrphan()
		
		
		// inserts given node into the "open" list
		// descending order by number of dependents
		public void openInsert(Node newNode) {
			// if list is empty
			if(open == null) {
				open = newNode;
				return;
			}
			
			// compare list head
			if(newNode.dependentCount > open.dependentCount) {
				newNode.next = open;
				open = newNode;
				return;
			}
			
			// insert into correct place in the list
			Node nav = open;
			while(nav.next != null && 
					newNode.dependentCount <= nav.next.dependentCount) {
				nav = nav.next;
			}
			newNode.next = nav.next;
			nav.next = newNode;
		} // end openInsert()
		
		
		// removes and returns the list head
		public Node openRemove() {
			if(open != null) {
				Node ret = open;
				open = open.next;
				ret.next = null;
				return ret;
			}
			return null;
		} // end openRemove()
		
		
		// prints the "open" list to debug file
		public void printList(PrintWriter debug) {
			if(open != null) {
				Node nav = open;
				debug.print("open-> ");
				while(nav.next != null) {
					debug.print("(" + nav.jobId + ", " + nav.next.jobId + ")-> ");
					nav = nav.next;
				}
				debug.println("(" + nav.jobId + ", null)-> null");
			}	
		} // end printList()
			
		
		// places available jobs onto available processors
		// jobs are removed from "open" list, by the most number of dependents first
		//  and placed onto the first available processor
		public void loadProcAry() {
			int availProc;
			do {
				availProc = findProcessor();
				if(availProc > 0) {
					procUsed++;
					
					Node newJob = openRemove();
					if(newJob != null) {
						int jobId = newJob.jobId;
						int jobTime = newJob.jobTime;
						
						procAry[availProc].doWhichJob = jobId;
						procAry[availProc].timeRemain = jobTime;
						
						putJobOnTable(availProc, jobId, jobTime);
					}
					
				}
			} while((availProc > 0) && (open != null) && (procUsed < numProcs));
		} // end loadProcAry()
		
		
		// returns the first available processor
		public int findProcessor() {
			for(int i=1; i<=numProcs; i++) {
				
				if(procAry[i].timeRemain <= 0) {
					return i;
				}
			}
			return -1;
		} // end findProcessor()
		
		
		// places the given job and its corresponding processor 
		//  onto the scheduleTable[][] by length of its processing time
		public void putJobOnTable(int availProc, int jobId, int jobTime) {
			int time = currentTime;
			int endTime = time + jobTime;
			
			while(time < endTime) {
				scheduleTable[availProc][time] = jobId;
				time++;
			}
		} // end putJobOnTable()		
		
		
		// prints the schedule table to given outFile
		public void printScheduleTable(PrintWriter outFile) {
			// print time row
			outFile.print(" time|-");
			for(int i=0; i<=totalJobTimes; i++) {
				if(length(i+1) == 1) {
					outFile.print(i + "---");
				} else {
					outFile.print(i + "--");
				}
			}
			outFile.println();
			
			
			for(int i=1; i<=numProcs; i++) {
				// print processor number
				outFile.print("P(");
				outFile.format("%2d", i);
				outFile.print(")|");
				
				
				// print jobs for each processor
				for(int j=0; j<=totalJobTimes; j++) {
					if(scheduleTable[i][j] == 0) {
						outFile.print(" -");
					} else {
						outFile.format("%2d", scheduleTable[i][j]);
					}
					outFile.print(" |");
				}
				outFile.println();
				
				
				// print row divider
				outFile.print("-----");
				for(int k=0; k<=totalJobTimes; k++) {
					outFile.print("----");
				}
				outFile.println();
			}
			outFile.println();
			outFile.println();
		} // end printScheduleTable()
			
		
		// checks for a cycle in the graph, returns true if a cycle exists
		public boolean checkCycle() {
			if(open == null && !graphIsEmpty() && processorsDone()) {
				return true;
			}
			return false;
		} // end checkCycle()
		
		
		// returns true if graph is empty
		public boolean graphIsEmpty() {
			for(int i=1; i<=numNodes; i++) {
				if(onGraphAry[i] != 0) return false;
			}
			return true;
		} // end graphIsEmpty()
		
		
		// returns true if all processors are free
		public boolean processorsDone() {
			for(int i=1; i<=numProcs; i++) {
				if(procAry[i].timeRemain > 0) return false;
			}
			return true;
		} // end processorsDone()
		
		
		// updates all processor times, decrement by 1
		public void updateProcTime() {
			for(int i=1; i<=numProcs; i++) {
				if(procAry[i].timeRemain > 0) {
					procAry[i].timeRemain--;
				}
			}
		} // end updateProcTime()
		
		
		// increments currentTime by 1
		public void incrementCurrentTime() {
			currentTime++;
		} // end incrementCurrentTime()
		
		
		// removes a given job from the graph
		public void removeJobFromGraph(int jobId) {
			onGraphAry[jobId] = 0;
		} // end removeJobFromGraph()
		
		
		// returns the jobId of the first free processor, 0 if none are done
		public int findDoneProc() {
			int jobId;
			for(int i=1; i<=numProcs; i++) {			
				if(procAry[i].timeRemain == 0) {
					procAry[i].timeRemain = -1;
					jobId = procAry[i].doWhichJob;
					procAry[i].doWhichJob = -1;
					procUsed--;
					return jobId;
				}
			}
			return 0;
		} // end findDoneProc()
		
		
		// deletes a node's outgoing edges from the graph
		public void deleteEdge(int jobId) {
			for(int dependent=1; dependent<=numNodes; dependent++) {
				if(adjMatrix[jobId][dependent] > 0) {
					parentCountAry[dependent]--;
					jobAry[dependent].parentCount--;;
				}
			}
		} // end deleteEdge()
		
		
		// returns the length of given integer
		public int length(int x) {
			return String.valueOf(x).length();
		} // end length()
		
		
		//=====================================================================
		public static class Node {
			int jobId;
			int jobTime;
			int dependentCount;
			Node next;
			
			// constructor
			public Node(int id, int time, int count) {
				jobId = id;
				jobTime = time;
				dependentCount = count;
				next = null;
			}
		} // end nested class: Node
			
		//=====================================================================
		public static class Jobs {
			int jobTime;
			int onWhichProc;
			int onOpen;
			int parentCount;
			int dependentCount;
			
			// constructor
			public Jobs() {
				
			}
		} // end nested class: Jobs
			
		//=====================================================================
		public static class Proc {
			int doWhichJob;
			int timeRemain;
			
			// constructor
			public Proc() {
				doWhichJob = -1;
				timeRemain = -1;
			}
		} // end nested class: Proc
		//=====================================================================
		
	} // end class: Scheduling

} // end wrapper class: Main