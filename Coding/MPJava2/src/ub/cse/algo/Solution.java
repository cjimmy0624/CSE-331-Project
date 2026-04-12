package ub.cse.algo;

import java.util.*;

public class Solution {

    private Info info;
    private Graph graph;
    private ArrayList<Client> clients;
    private ArrayList<Integer> bandwidths;

    /**
     * Basic Constructor
     *
     * @param info: data parsed from input file
     */
    public Solution(Info info) {
        this.info = info;
        this.graph = info.graph;
        this.clients = info.clients;
        this.bandwidths = info.bandwidths;
    }

    /**
     * Method that returns the calculated
     * SolutionObject as found by your algorithm
     *
     * @return SolutionObject containing the paths, priorities and bandwidths
     */
    public SolutionObject outputPaths() {
        SolutionObject sol = new SolutionObject();
        /* TODO: Your solution goes here */
        // 1. Find one possible path for each node
        HashMap<Integer,ArrayList<Integer>> paths = Traversals.bfsPaths(this.graph,this.clients);
        sol.paths = paths;
        ArrayList<Integer> bandwidthList = new ArrayList<>(this.bandwidths);
        sol.bandwidths = bandwidthList;
        return findPathGivenBandwidth(sol);
    }
    private SolutionObject findPathGivenBandwidth(SolutionObject sol) {
        HashMap<Integer,Integer> delayPaths = Simulator.run(this.graph,this.clients,sol);
        ArrayList<Integer> clientsWDelay = new ArrayList<>();

        // 2. Figure out which clients have delays
        for (Client client : this.clients) {
            if(delayPaths.get(client.id) > info.shortestDelays.get(client.id) + 16){
                clientsWDelay.add(client.id);
            }

        }

        //3. Figure out which nodes have delays
        int count = 0;
        int maxPathLength = 0;
        HashMap<Integer,ArrayList<Integer>> nodeMap = new HashMap<>();
        for (ArrayList<Integer> clientPath: sol.paths.values()){
            if (clientPath.size() > maxPathLength){
                maxPathLength = clientPath.size();
            }
        }
        HashMap<Integer,ArrayList<Integer>> congestNodes = new HashMap<>();
        while (count < maxPathLength) {
            ArrayList<Integer> nodeAtCount = new ArrayList<>();
            for (Client client : this.clients) {
                int clientID = client.id;
                ArrayList<Integer> path = sol.paths.get(clientID);
                if (count < path.size()) {
                    nodeAtCount.add(path.get(count));
                }
            }
            nodeMap = findNodeAtCount(nodeAtCount,sol.paths,count);

            for (int nodeID: nodeMap.keySet()){
                if (nodeMap.get(nodeID).size() > bandwidths.get(nodeID)) {
                    // for each client at this congested node
                    for (int clientID : nodeMap.get(nodeID)) {
                        if (!congestNodes.containsKey(clientID)) {
                            congestNodes.put(clientID, new ArrayList<>());
                        }
                        if (!congestNodes.get(clientID).contains(nodeID)) {
                            congestNodes.get(clientID).add(nodeID);
                        }
                    }
                }
            }
            count++;
        }
        if (clientsWDelay.isEmpty() || congestNodes.isEmpty()){
            return sol;
        }

        float currentRev = Revenue.revenue(info, sol, delayPaths, false, false, true);

        HashMap<Integer, Client> newMap = new HashMap<>();
        for(Client clients : this.clients){
            newMap.put(clients.id, clients);
        }

        //Sort by most delayed clients
        for(int i = 0; i < clientsWDelay.size(); i++){
            for(int j = i + 1; j < clientsWDelay.size(); j++){
                int client1 = clientsWDelay.get(i);
                int client2 = clientsWDelay.get(j);

                int delay1 = delayPaths.get(client1) - info.shortestDelays.get(client1);
                int delay2 = delayPaths.get(client2) - info.shortestDelays.get(client2);

                float rev1 = delay1 * newMap.get(client1).payment;
                float rev2 = delay2 * newMap.get(client2).payment;

                if(rev2 > rev1){
                    clientsWDelay.set(i, client2);
                    clientsWDelay.set(j, client1);
                }
            }
        }

        for (Integer clientID : clientsWDelay) {
            if (congestNodes.containsKey(clientID)) {
                // find the client object
                Client client = null;
                for (Client c : this.clients) {
                    if (c.id == clientID) {
                        client = c;
                        break;
                    }
                }


                // now you have access to client.alpha
                ArrayList<Integer> newPath = bfsPathAvoidCongestNode(clientID, congestNodes.get(clientID));
                if (newPath.size() > 1 && newPath.get(0) == this.graph.contentProvider) {
                    int newPathDelay = newPath.size() - 1;
                    int shortestDelay = info.shortestDelays.get(clientID);

                    if (newPathDelay <= client.alpha * shortestDelay) {
                        ArrayList<Integer> oldPath = sol.paths.get(clientID);

                        if(newPath.equals(oldPath)){
                            continue;
                        }

                        sol.paths.put(clientID, newPath);
                        float newRev = Revenue.revenue(info, sol, delayPaths, false, false, true);

                        if(newRev< currentRev){
                            sol.paths.put(clientID, oldPath);
                        }else{
                            currentRev = newRev;
                        }
                    }
                }
                // else keep original path
            }
        }
        delayPaths = Simulator.run(this.graph,this.clients,sol);
        return sol;
    }
    private HashMap<Integer, ArrayList<Integer>> findNodeAtCount(ArrayList<Integer> nodeAtCount, HashMap<Integer, ArrayList<Integer>> generatedPaths, int count) {
        HashMap<Integer, ArrayList<Integer>> nodeMap = new HashMap<>();
        for (int i = 0; i < this.clients.size(); i++){
            int clientID = this.clients.get(i).id;
            ArrayList<Integer> path = generatedPaths.get(clientID);

            if (count < path.size()){
                int nodeID = path.get(count);
                if (!nodeMap.containsKey(nodeID)){
                    nodeMap.put(nodeID, new ArrayList<>());
                }
                nodeMap.get(nodeID).add(clientID);
            }
        }
        return nodeMap;
    }

    private ArrayList<Integer> bfsPathAvoidCongestNode(int clientId, ArrayList<Integer> congestNodesAtClient) {
        int[] priors = new int[this.graph.size()];
        Arrays.fill(priors, -1);
        ArrayList<Integer> path = new ArrayList<>();

        // Run BFS, finding the nodes parent in the shortest path
        Queue<Integer> searchQueue = new LinkedList<>();
        searchQueue.add(this.graph.contentProvider);
        //Mark as visited
        priors[this.graph.contentProvider] = this.graph.contentProvider;
        while (!searchQueue.isEmpty()) {
            int node = searchQueue.poll();
            for (int neighbor : this.graph.get(node)) {
                if (priors[neighbor] == -1
                        && !congestNodesAtClient.contains(neighbor)) {
                    priors[neighbor] = node;
                    searchQueue.add(neighbor);
                }
            }
        }

        if(priors[clientId] == -1){
            return path;
        }

        // Reconstruct single path for this client
        int currentNode = clientId;
        while (currentNode != this.graph.contentProvider) {
            path.add(0, currentNode);
            currentNode = priors[currentNode];
        }
        path.add(0, this.graph.contentProvider);
        return path;
    }


}