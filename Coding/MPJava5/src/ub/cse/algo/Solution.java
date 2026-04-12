package ub.cse.algo;

import java.util.ArrayList;
import java.util.HashMap;

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
        sol.paths = Traversals.bfsPaths(this.graph, this.clients);
        sol.bandwidths = new ArrayList<>(bandwidths);

        HashMap<Integer, Client> clientsMap = new HashMap<>();
        for (Client client : clients) {
            clientsMap.put(client.id, client);
        }
        return sol;
    }
}
