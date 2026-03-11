# Run LEO Mission Optimization Test

Runs `LEOMissionOptimizationTest`, downloading Orekit data if needed.

## Steps

1. **Ensure orekit-data.zip is present** in `src/test/resources/`:
   - Check if `src/test/resources/orekit-data.zip` exists.
   - If missing, download it from GitHub using:
     ```bash
     curl -L "https://codeload.github.com/eSpace-epfl/orekit-data/zip/refs/heads/master" \
       -o src/test/resources/orekit-data.zip
     ```
   - Then patch the `itrf-versions.conf` entry inside the zip to use Orekit 13.x plain-prefix format (replace regex metacharacters like `\.\d\d` with plain prefixes like `.`). Use the Python script below:
     ```python
     import zipfile, os

     new_itrf_conf = """# itrf-versions.conf — plain prefix format for Orekit 13.x
     eopc04_05.                        ------           ------          ITRF-2005
     eopc04_05_IAU2000.                ------           ------          ITRF-2005
     eopc04_08.                        ------           ------          ITRF-2008
     eopc04_08_IAU2000.                ------           ------          ITRF-2008
     eopc04_14.                        ------           ------          ITRF-2014
     eopc04_14_IAU2000.                ------           ------          ITRF-2014
     bulletina-                        ------         2007-02-02        ITRF-2000
     bulletina-                      2007-02-02       2011-01-28        ITRF-2005
     bulletina-                      2011-01-28       2018-03-23        ITRF-2008
     bulletina-                      2018-03-23         ------          ITRF-2014
     bulletinb_IAU1980-                ------           ------          ITRF-2005
     bulletinb_IAU2000-                ------           ------          ITRF-2005
     bulletinb-                        ------         2011-02-01        ITRF-2005
     bulletinb-                      2011-02-01       2018-03-01        ITRF-2008
     bulletinb-                      2018-03-01         ------          ITRF-2014
     bulletinb.                        ------         2011-02-01        ITRF-2005
     bulletinb.                      2011-02-01       2017-03-01        ITRF-2008
     bulletinb.                      2017-03-01         ------          ITRF-2014
     finals.                           ------         2007-02-02        ITRF-2000
     finals.                         2007-02-02       2011-01-28        ITRF-2005
     finals.                         2011-01-28       2018-03-23        ITRF-2008
     finals.                         2018-03-23         ------          ITRF-2014
     finals2000A.                      ------         2007-02-02        ITRF-2000
     finals2000A.                    2007-02-02       2011-01-28        ITRF-2005
     finals2000A.                    2011-01-28       2018-03-23        ITRF-2008
     finals2000A.                    2018-03-23         ------          ITRF-2014
     """

     src = "src/test/resources/orekit-data.zip"
     tmp = "src/test/resources/orekit-data-tmp.zip"
     with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
         for item in zin.infolist():
             if "itrf-versions.conf" in item.filename:
                 zout.writestr(item, new_itrf_conf.encode("utf-8"))
             else:
                 zout.writestr(item, zin.read(item.filename))
     os.replace(tmp, src)
     ```

2. **Run the test**:
   ```bash
   gradle test --tests "com.smousseur.orbitlab.simulation.mission.optimizer.LEOMissionOptimizationTest"
   ```

3. **Report results** — show whether the test passed and, if it failed, display the failure message from the test XML report at `build/test-results/test/TEST-*.xml`.

## Notes

- `src/test/resources/orekit-data.zip` is gitignored — it must be downloaded locally before running the test.
- The data source (`eSpace-epfl/orekit-data` on GitHub) uses the old Orekit regex format for `itrf-versions.conf`; the patching step is required for compatibility with Orekit 13.x.
- `gradle` must be available on `PATH` (no wrapper in repo; use the system Gradle installation).
