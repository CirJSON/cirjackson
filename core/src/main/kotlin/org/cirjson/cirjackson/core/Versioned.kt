package org.cirjson.cirjackson.core

/**
 * Interface that those CirJackson components that are explicitly versioned will implement. Intention is to allow both
 * plug-in components (custom extensions) and applications and frameworks that use CirJackson to detect exact version of
 * CirJackson in use. This may be useful for example for ensuring that proper CirJackson version is deployed (beyond
 * mechanisms that deployment system may have), as well as for possible workarounds.
 */
interface Versioned {

    /**
     * Method called to detect version of the component that implements this interface; returned version should never be
     * null, but may return specific "not available" instance (see [Version] for details).
     *
     * @return Version of the component
     */
    fun version(): Version

}