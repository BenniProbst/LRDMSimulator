package org.lrdm;

import java.util.*;
import java.util.stream.Stream;

public class MirrorCursor {
    private int numTargetMirrors;
    private final List<Mirror> mirrors;
    private double faultProbability = 0.01;
    private final Random random;

    public MirrorCursor(int numMirrors, int fileSize, Properties props){
        numTargetMirrors = numMirrors;
        mirrors = new ArrayList<>();

        // create the mirrors
        for (int i = 0; i < numMirrors; i++) {
            mirrors.add(new Mirror(i, 0, props));
        }
        mirrors.get(0).setRoot(true);
        faultProbability = Double.parseDouble(props.getProperty("fault_probability"));
        random = new Random();

        //put a new data package on the first mirror
        DataPackage initialData = new DataPackage(fileSize);
        initialData.increaseReceived(fileSize);
        mirrors.get(0).setDataPackage(initialData);
    }

    /**Returns aks Mirrors of the net.
     *
     * @return List of all {@link Mirror}s
     */
    public List<Mirror> getMirrors() {
        return mirrors;
    }

    /**Get Mirrors sorted by ID in ascending order.
     *
     * @return {@link List} or mirrors sorted in ascending order by ID
     */
    public List<Mirror> getMirrorsSortedById() {
        return mirrors.stream().sorted(Comparator.comparingInt(Mirror::getID)).toList();
    }

    /**Get Mirror stream.
     *
     * @return {@link Stream} of mirrors
     */
    public Stream<Mirror> getMirrorStream() {
        return mirrors.stream();
    }

    /**
     * @return number of mirrors in the net (regardless of their state)
     */
    public int getNumMirrors() {
        return mirrors.size();
    }

    /**Inspect the network for mirrors in the STOPPED state to remove them from the network.
     * Else calls {@link Mirror#timeStep(int)}
     *
     * @param simTime current simulation time
     */
    private void handleMirrors(int simTime) {
        //find stopped mirrors to remove them or invoke timeStep on the active mirrors
        List<Mirror> stoppedMirrors = new ArrayList<>();
        for (Mirror m : mirrors) {
            if (m.getState() == Mirror.State.STOPPED) {
                stoppedMirrors.add(m);
            } else {
                if(random.nextDouble() < faultProbability && !m.isRoot()) {
                    m.crash(simTime);
                }
                m.timeStep(simTime);
            }
        }
        mirrors.removeAll(stoppedMirrors);
    }

    public int getNumTargetMirrors() {
        return numTargetMirrors;
    }

    public void setNumTargetMirrors(int numTargetMirrors) {
        this.numTargetMirrors = numTargetMirrors;
    }

    /**
     * @return current number of ready mirrors
     */
    public int getNumReadyMirrors() {
        int ret = 0;
        for (Mirror m : mirrors) {
            if (m.getState() == Mirror.State.READY || m.getState() == Mirror.State.HASDATA) {
                ret++;
            }
        }
        return ret;
    }
}
