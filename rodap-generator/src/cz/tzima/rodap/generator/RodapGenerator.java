package cz.tzima.rodap.generator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

public class RodapGenerator {
    private static final int BUFFER_SIZE = 1024 * 1024;

    private static final int MIN_FNAME_LENGTH = 2;
    private static final int MIN_LNAME_LENGTH = 2;

    private static final int MAX_NAME_LENGTH = 20;
    private static final int MAX_FNAME_LENGTH = MAX_NAME_LENGTH - MIN_FNAME_LENGTH - 1;

    @SuppressWarnings("unchecked")
    private static final Entry<String, Integer>[] QUANTIFIERS = new Entry[]{
        new SimpleEntry<String, Integer>("G", (int) Math.pow(1000, 3)),
        new SimpleEntry<String, Integer>("M", (int) Math.pow(1000, 2)),
        new SimpleEntry<String, Integer>("k", (int) Math.pow(1000, 1)),
        new SimpleEntry<String, Integer>("",  (int) Math.pow(1000, 0))
    };

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 6) {
            System.out.println("Use: rodapgen <file> <count> [-s|--seed <num>] [-b|--bufferSize <num>] [-q | --quiet]");
            return;
        }

        final long t0 = System.currentTimeMillis();
        final Config config = Config.parseFrom(args);
        final int bufferSize = config.bufferSize.orElse(BUFFER_SIZE);
        final long seed = config.seed.orElse(System.currentTimeMillis());
        final File file = new File(config.filename);

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file), bufferSize)) {
            generateRecords(out, new Random(seed), config.count, writtenItems -> {
                if (!config.quiet.orElse(false) && writtenItems % 1_000_000 == 0) {
                    System.out.printf("written %d of %d records\n", writtenItems, config.count);
                }
            });
        }

        if (!config.quiet.orElse(false)) {
            System.out.printf("Generated file %s with %d records (%s) [using seed %d] in %d ms.\n", file.getAbsolutePath(),
                    config.count, number(file.length()) + 'B', seed ,System.currentTimeMillis() - t0);
        }
    }

    static void generateRecords(OutputStream out, Random random, long n, Consumer<Integer> written) throws IOException {
        final byte[] name = new byte[20];
        final byte[] number = new byte[9];
        final byte[] newLine = "\n".getBytes();
        final byte[] separator = ";".getBytes();

        for (int i = 0; i < n; i++) {
            random.nextBytes(name);
            random.nextBytes(number);

            int fnameLength = rand(random, MIN_FNAME_LENGTH, MAX_FNAME_LENGTH);
            int lnameLength = rand(random, MIN_LNAME_LENGTH, MAX_NAME_LENGTH - fnameLength - 1);

            for (int j = 0; j < name.length; j++) {
                if (j < fnameLength || (j > fnameLength && j < fnameLength + lnameLength + 1)) {
                    name[j] = (byte) (((name[j] & 0xFF) % ('Z' - 'A' + 1)) + (j == 0 ? 'A' : 'a'));
                } else {
                    name[j] = ' ';
                }
            }

            for (int j = 0; j < number.length; j++) {
                number[j] = (byte) ((number[j] & 0xFF) % 10 + '0');
            }

            out.write(name);
            out.write(separator);
            out.write(number);

            if (i != n - 1) {
                out.write(newLine);
            }

            written.accept(i + 1);
        }

        out.flush();
    }

    static int rand(Random random, int a, int b) {
        return a + random.nextInt(b - a + 1);
    }

    static String number(long number) {
        for (Entry<String, Integer> quantifier : QUANTIFIERS) {
            double val = number / (double) quantifier.getValue();
            if (Math.floor(val) > 0) {
                return String.format("%.2f%s", val, quantifier.getKey());
            }
        }

        return String.valueOf(number);
    }

    static long number(String number) {
        char last = number.charAt(number.length() - 1);
        if (Character.isLetter(last)) {
            double f = Double.valueOf(number.substring(0, number.length() - 1));

            for (Entry<String, Integer> quantifier : QUANTIFIERS) {
                if (quantifier.getKey().equals(String.valueOf(last))) {
                    return (long) (f * quantifier.getValue());
                }
            }
        }

        return Long.valueOf(number);
    }

    static final class Config {
        public String filename;
        public Long count;
        public Optional<Long> seed = Optional.empty();
        public Optional<Integer> bufferSize = Optional.empty();
        public Optional<Boolean> quiet = Optional.empty();

        static Config parseFrom(String[] args) throws IllegalArgumentException {
            Config config = new Config();

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-s") || args[i].equals("--seed")) {
                    config.seed = Optional.of(Long.valueOf(args[++i]));
                } else if (args[i].equals("-b") || args[i].equals("--bufferSize")) {
                    config.bufferSize = Optional.of(Integer.valueOf(args[++i]));
                } else if (args[i].equals("-q") || args[i].equals("--quiet")) {
                    config.quiet = Optional.of(true);
                } else if (config.filename == null) {
                    config.filename = args[i];
                } else if (config.count == null) {
                    config.count = number(args[i]);
                }
            }

            return config;
        }
    }
}
