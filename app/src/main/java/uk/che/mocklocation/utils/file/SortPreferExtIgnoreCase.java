package uk.che.mocklocation.utils.file;

import java.util.Comparator;

public class SortPreferExtIgnoreCase implements Comparator<Object> {

    private String fileExt;

    public SortPreferExtIgnoreCase(String fileExt) {
        if (fileExt != null)
            this.fileExt = fileExt.toLowerCase();
    }

    public int compare(Object o1, Object o2) {
        String s1 = (String) o1;
        String s2 = (String) o2;

        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        if (fileExt != null) {
            if (s1.endsWith(fileExt) && !s2.endsWith(fileExt))
                return -1;
            if (!s1.endsWith(fileExt) && s2.endsWith(fileExt))
                return 1;
        }

        return s1.compareTo(s2);
    }
}