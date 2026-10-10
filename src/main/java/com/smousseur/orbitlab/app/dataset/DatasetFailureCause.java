package com.smousseur.orbitlab.app.dataset;

/**
 * Why an installation of the dataset gave up, in the terms the start-up screen explains to the
 * user: each cause calls for a different action on their side.
 */
public enum DatasetFailureCause {

  /**
   * The release could not be reached or stopped answering: no connection, no response in time, no
   * byte received for too long, a server error or a rate limit, three times in a row without the
   * download moving forward.
   */
  NETWORK,

  /** The disk has no room for what is left to download, before starting or while writing. */
  DISK_FULL,

  /** What was downloaded does not match the manifest's fingerprint, three times in a row. */
  CORRUPT,

  /** The release does not hold a file the manifest expects: it answered 404 or 410. */
  NOT_FOUND,

  /**
   * Writing or reading the dataset directory failed for another reason than space, such as rights.
   */
  LOCAL_IO
}
