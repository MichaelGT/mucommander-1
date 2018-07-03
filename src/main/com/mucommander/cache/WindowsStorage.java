/*
 * This file is part of trolCommander, http://www.trolsoft.ru/en/soft/trolcommander
 * Copyright (C) 2013-2016 Oleg Trifonov
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mucommander.cache;

import com.mucommander.PlatformManager;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Window;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores windows sizes and positions
 */
public class WindowsStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsStorage.class);
    private static final String STORAGE_FILE_NAME = "windows.list";
    private static Reference<WindowsStorage> instance;

    private Map<String, Record> records;

    public static class Record {

        private final int left, top, width, height;

        public Record(String s) {
            String[] val = s.split(",");
            this.left = Integer.parseInt(val[0].trim());
            this.top = Integer.parseInt(val[1].trim());
            this.width = Integer.parseInt(val[2].trim());
            this.height = Integer.parseInt(val[3].trim());
        }

        public Record(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Record)) {
                return false;
            }
            Record rec = (Record) obj;
            return left == rec.left && top == rec.top && width == rec.width && height == rec.height;
        }

        @Override
        public String toString() {
            return String.valueOf(left) + ',' + top + ',' + width + ',' + height;
        }

        private void apply(Window window) {
            applyPos(window);
            window.setSize(width, height);
        }

        private void applyPos(Window window) {
            window.setLocation(left, top);
        }

    }

    public static WindowsStorage getInstance() {
        WindowsStorage windowsStorage = instance != null ? instance.get() : null;
        if (windowsStorage == null) {
            windowsStorage = new WindowsStorage();
            instance = new WeakReference<>(windowsStorage);
        }
        return windowsStorage;
    }

    public Record get(String key) {
        return getRecords().get(key);
    }

    public void put(String key, Record rec) {
        if (isStoreData()) {
            Record prev = getRecords().put(key, rec);
            if (prev == null || !prev.equals(rec)) {
                save();
            }
        }
    }

    private void save() {
        try {
            save(getHistoryFile());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void put(Window window, String suffix) {
        if (isStoreData()) {
            Record rec = new Record(window.getLocation().x, window.getLocation().y, window.getWidth(), window.getHeight());
            String key = getKey(window, suffix);
            put(key, rec);
        }
    }

    public boolean init(Window window, String suffix, boolean storeSizes) {
        if (isStoreData()) {
            String key = getKey(window, suffix);
            Record rec = get(key);
            if (rec != null && rec.width > 0 && rec.height > 40) {
                if (storeSizes) {
                    rec.apply(window);
                } else {
                    rec.applyPos(window);
                }
                return true;
            }
        }
        return false;
    }

    public void clear() {
        if (records != null) {
            records.clear();
        }
        save();
    }

    private String getKey(Window window, String suffix) {
        Class c = window.getClass();
        String key = c.getCanonicalName();
        if (key == null) {
            key = c.getPackage().getName() + '.' + c.getName();
        }
        if (suffix != null) {
            key += '#' + suffix;
        }
        return key;
    }

    private Map<String, Record> getRecords() {
        if (records == null) {
            records = new HashMap<>();
            try {
                load(getHistoryFile());
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return records;
    }

    private void load(AbstractFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int index = line.indexOf('=');
                if (index < 0) {
                    continue;
                }
                String key = line.substring(0, index);
                String val = line.substring(index + 1);
                try {
                    records.put(key, new Record(val));
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void save(AbstractFile file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(file.getOutputStream()))) {
            for (String key : getRecords().keySet()) {
                if (key == null) {
                    continue;
                }
                writer.write(key);
                writer.write('=');
                writer.write(records.get(key).toString());
                writer.write('\n');
            }
        }
    }

    private static synchronized AbstractFile getHistoryFile() throws IOException {
        return PlatformManager.getPreferencesFolder().getChild(STORAGE_FILE_NAME);
    }

    private boolean isStoreData() {
        return MuConfigurations.getPreferences().getVariable(MuPreference.STORE_WINDOWS_SIZES_AND_LOCATIONS, MuPreferences.DEFAULT_STORE_WINDOWS_SIZES_AND_LOCATIONS);
    }

}
