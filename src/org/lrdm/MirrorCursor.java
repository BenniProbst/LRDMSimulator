package org.lrdm;

import org.lrdm.util.IDGenerator;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Stream;

/**
 * Represents the cursor for the RDM network. Maintains a list of {@link Mirror}s and provides methods to access them.
 */
public class MirrorCursor {
    private int numTargetMirrors;
    private final List<Mirror> mirrors;
    private int mirrorIterator = -1;
    private final Properties props;
    private final double faultProbability;
    private final SecureRandom random;

    public MirrorCursor(int numMirrors, int fileSize, Properties props){
        numTargetMirrors = numMirrors;
        mirrors = new ArrayList<>();

        faultProbability = Double.parseDouble(props.getProperty("fault_probability"));
        random = new SecureRandom();
        this.props = props;

        // create the mirrors and put a new data package on the first mirror
        createMirrors(numMirrors, 0);
        if (!mirrors.isEmpty()) {
            mirrors.get(0).setRoot(true);

            DataPackage initialData = new DataPackage(fileSize);
            initialData.increaseReceived(fileSize);
            mirrors.get(0).setDataPackage(initialData);
        }
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

    /**
     * @return Number of usable mirrors
     *         läuft den globalen Iterator bis zum Ende durch und zählt nur Mirrors,
     *         die isUsableForNetwork() liefern.
     *         Hinweis: Der Iterator steht danach am Ende.
     */
    public int getNumUsableMirrors() {
        int usable = 0;

        for (Mirror m : mirrors) {
            if (m.isUsableForNetwork()) {
                usable++;
            }
        }

        return usable;
    }

    /**
     * @return a list of mirrors that are usable for the network.
     */
    public List<Mirror> getNonStopMirrors(){
        List<Mirror> nonStopMirrors = new ArrayList<>();
        for (Mirror m : mirrors) {
            if (m.isUsableForNetwork()) {
                nonStopMirrors.add(m);
            }
        }
        return nonStopMirrors;
    }

    /**Inspect the network for mirrors in the STOPPED state to remove them from the network.
     * Else calls {@link Mirror#timeStep(int)}
     *
     * @param simTime current simulation time
     */
    public void handleMirrors(int simTime) {
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

    // java
    /**
     * Creates the given number of mirrors and adds them to the cursor's list.
     *
     * @param numberOfMirrors the number of mirrors to add
     * @param simTime         the current simulation time
     * @return a set of added {@link Mirror}s
     */
    public Set<Mirror> createMirrors(int numberOfMirrors, int simTime) {
        if(mirrorIterator >= mirrors.size()) mirrorIterator = mirrors.size() - 1;
        int count = Math.max(0, numberOfMirrors);
        Set<Mirror> created = new LinkedHashSet<>(count);
        IDGenerator idGen = IDGenerator.getInstance();

        for (int i = 0; i < count; i++) {
            Mirror mirror = new Mirror(idGen.getNextID(), simTime, props);
            created.add(mirror);
        }

        // zur internen Liste hinzufügen und stabil sortieren
        this.mirrors.addAll(created);
        this.mirrors.sort(Comparator.comparingInt(Mirror::getID));

        return created;
    }

    /**
     * Reset the mirror cursor to the beginning.
     */
    public void resetMirrorCursor() {
        mirrorIterator = -1;
    }

    /**
     * Gibt den nächsten verfügbaren Mirror aus dem Iterator zurück.
     * Vereinfacht die Interface-Trennung zwischen BuildAsSubstructure und Topology-Implementierungen.
     *
     * @return Der nächste verfügbare Mirror oder null, wenn kein Mirror verfügbar ist
     */
    public final Mirror getNextMirror() {
        if (hasNextMirror()) {
            while (hasNextMirror()) {
                mirrorIterator++;
                Mirror mirror = mirrors.get(mirrorIterator);
                if(mirror.isUsableForNetwork())return mirror;
            }
        }
        return null;
    }

    /**
     * Prüft, ob weitere Mirrors im Iterator verfügbar sind.
     *
     * @return true, wenn weitere Mirrors verfügbar sind
     */
    public final boolean hasNextMirror() {
        return mirrorIterator < mirrors.size() - 1;
    }
}
