// Compile a set of tzfile-formatted files into a single file plus
// an index file.
//
// The compilation is controlled by a setup file, which is provided as a
// command-line argument.  The setup file has the form:
//
// Link <toName> <fromName>
// ...
// <zone filename>
// ...
//
// Note that the links must be declared prior to the zone names.  A
// zone name is a filename relative to the source directory such as
// 'GMT', 'Africa/Dakar', or 'America/Argentina/Jujuy'.
//
// Use the 'zic' command-line tool to convert from flat files
// (e.g., 'africa', 'northamerica') into a suitable source directory
// hierarchy for this tool (e.g., 'data/Africa/Abidjan').
//
// Example:
//     zic -d data tz2007h
//     javac ZoneCompactor.java
//     java ZoneCompactor setup /path/to/data /path/to/zoneinfo 2007h
//     <produces zoneinfo.dat and zoneinfo.idx>

import java.io.*;
import java.util.*;

public class ZoneCompactor {
    // Zone name synonyms
    Map links = new HashMap();
    // File starting bytes by zone name
    Map starts = new HashMap();
    // File lengths by zone name
    Map lengths = new HashMap();
    // Raw GMT offsets by zone name
    Map offsets = new HashMap();
    int start = 0;
    // Maximum number of characters in a zone name, including '\0' terminator
    private static final int MAXNAME = 40;

    // Concatenate the contents of 'inFile' onto 'out'
    // and return the contents as a byte array.
    private static byte[] copyFile(File inFile, OutputStream out)
            throws Exception {
        byte[] ret = new byte[0];
        InputStream in = new FileInputStream(inFile);
        byte[] buf = new byte[8192];
        while (true) {
            int nbytes = in.read(buf);
            if (nbytes == -1) {
                break;
            }
            out.write(buf, 0, nbytes);
            byte[] nret = new byte[ret.length + nbytes];
            System.arraycopy(ret, 0, nret, 0, ret.length);
            System.arraycopy(buf, 0, nret, ret.length, nbytes);
            ret = nret;
        }
        out.flush();
        return ret;
    }

    // Write a 32-bit integer in network byte order
    private void writeInt(OutputStream os, int x) throws IOException {
        os.write((x >> 24) & 0xff);
        os.write((x >> 16) & 0xff);
        os.write((x >> 8) & 0xff);
        os.write(x & 0xff);
    }

    public ZoneCompactor(String setupFile, String dataDirectory, String outputDirectory, String versionStr)
            throws Exception {
        File zoneInfoFile = new File(outputDirectory, "zoneinfo.dat");
        zoneInfoFile.delete();
        OutputStream zoneInfo = new FileOutputStream(zoneInfoFile);
        BufferedReader rdr = new BufferedReader(new FileReader(setupFile));

        String s;
        while ((s = rdr.readLine()) != null) {
            s = s.trim();
            if (s.startsWith("Link")) {
                StringTokenizer st = new StringTokenizer(s);
                st.nextToken();
                String to = (String) st.nextToken();
                String from = (String) st.nextToken();
                links.put(from, to);
            } else {
                String link = (String) links.get(s);
                if (link == null) {
                    File f = new File(dataDirectory, s);
                    long length = f.length();
                    starts.put(s, new Integer(start));
                    lengths.put(s, new Integer((int) length));
                    start += length;
                    byte[] data = copyFile(f, zoneInfo);
                    TimeZone tz = ZoneInfo.make(s, data);
                    int gmtOffset = tz.getRawOffset();
                    offsets.put(s, new Integer(gmtOffset));
                }
            }
        }
        zoneInfo.close();
        // Fill in fields for links
        Iterator iter = links.keySet().iterator();
        while (iter.hasNext()) {
            String from = (String) iter.next();
            String to = (String) links.get(from);
            starts.put(from, starts.get(to));
            lengths.put(from, lengths.get(to));
            offsets.put(from, offsets.get(to));
        }
        File idxFile = new File(outputDirectory, "zoneinfo.idx");
        idxFile.delete();
        FileOutputStream idx = new FileOutputStream(idxFile);
        ArrayList l = new ArrayList();
        l.addAll(starts.keySet());
        Collections.sort(l);
        Iterator ziter = l.iterator();
        while (ziter.hasNext()) {
            String zname = (String) ziter.next();
            if (zname.length() >= MAXNAME) {
                System.err.println("Error - zone filename exceeds " +
                        (MAXNAME - 1) + " characters!");
            }
            byte[] znameBuf = new byte[MAXNAME];
            for (int i = 0; i < zname.length(); i++) {
                znameBuf[i] = (byte) zname.charAt(i);
            }
            idx.write(znameBuf);
            writeInt(idx, ((Integer) starts.get(zname)).intValue());
            writeInt(idx, ((Integer) lengths.get(zname)).intValue());
            writeInt(idx, ((Integer) offsets.get(zname)).intValue());
        }
        idx.close();
        // Write tzdata version to file
        File versionFile = new File(outputDirectory, "zoneinfo.version");
        BufferedWriter writer = new BufferedWriter(new FileWriter(versionFile));
        writer.write(versionStr);
        writer.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: java ZoneCompactor <setup> <data directory> <output directory> <tzdata version>");
            System.exit(0);
        }
        new ZoneCompactor(args[0], args[1], args[2], args[3]);
    }
}