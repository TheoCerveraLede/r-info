package rinfo.runtime;

/**
 * Contenido de una esquina de la ciudad.
 *
 * <p>Los contadores se tocan desde varios hilos de robot, así que el acceso
 * está sincronizado sobre la propia esquina.
 */
public final class Esquina {
    private int flores;
    private int papeles;
    private boolean obstaculo;

    public synchronized int getFlores() {
        return flores;
    }

    public synchronized void setFlores(int flores) {
        this.flores = Math.max(0, flores);
    }

    public synchronized int getPapeles() {
        return papeles;
    }

    public synchronized void setPapeles(int papeles) {
        this.papeles = Math.max(0, papeles);
    }

    public synchronized boolean tieneObstaculo() {
        return obstaculo;
    }

    public synchronized void setObstaculo(boolean obstaculo) {
        this.obstaculo = obstaculo;
    }

    public synchronized boolean tomarFlor() {
        if (flores <= 0) {
            return false;
        }
        flores--;
        return true;
    }

    public synchronized boolean tomarPapel() {
        if (papeles <= 0) {
            return false;
        }
        papeles--;
        return true;
    }

    public synchronized void dejarFlor() {
        flores++;
    }

    public synchronized void dejarPapel() {
        papeles++;
    }

    public synchronized void limpiar() {
        flores = 0;
        papeles = 0;
        obstaculo = false;
    }
}
