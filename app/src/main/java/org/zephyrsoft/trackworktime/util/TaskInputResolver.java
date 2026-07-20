/*
 * This file is part of TrackWorkTime (TWT).
 *
 * TWT is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * TWT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License 3.0 for more details.
 *
 * You should have received a copy of the GNU General Public License 3.0
 * along with TWT. If not, see <http://www.gnu.org/licenses/>.
 */
package org.zephyrsoft.trackworktime.util;

import org.zephyrsoft.trackworktime.model.Task;

import java.util.List;

/**
 * Decides what should happen when the user presses Start with a task name typed
 * into the main screen's task field but no task explicitly picked from the dropdown.
 * Pure logic, no Android dependencies, so it is unit-testable.
 */
public final class TaskInputResolver {

    public enum Kind {
        /** The typed name matches an existing active task — track on it directly. */
        USE_EXISTING,
        /** The typed name matches an existing inactive task — offer to reactivate it. */
        REACTIVATE,
        /** The typed name matches no task — offer to create it. */
        CREATE,
        /** Nothing typed — leave the existing default-task fallback in place. */
        NONE
    }

    public static final class Resolution {
        public final Kind kind;
        /** set for USE_EXISTING and REACTIVATE, otherwise null */
        public final Task task;
        /** set for CREATE (trimmed name), otherwise null */
        public final String newName;

        private Resolution(Kind kind, Task task, String newName) {
            this.kind = kind;
            this.task = task;
            this.newName = newName;
        }
    }

    private TaskInputResolver() {
        // utility class
    }

    public static Resolution resolve(String typed, List<Task> allTasks) {
        String name = typed == null ? "" : typed.trim();
        if (name.isEmpty()) {
            return new Resolution(Kind.NONE, null, null);
        }
        Task inactiveMatch = null;
        if (allTasks != null) {
            for (Task task : allTasks) {
                if (task == null || task.getName() == null) {
                    continue;
                }
                if (task.getName().equalsIgnoreCase(name)) {
                    if (task.isActive()) {
                        return new Resolution(Kind.USE_EXISTING, task, null);
                    } else if (inactiveMatch == null) {
                        inactiveMatch = task;
                    }
                }
            }
        }
        if (inactiveMatch != null) {
            return new Resolution(Kind.REACTIVATE, inactiveMatch, null);
        }
        return new Resolution(Kind.CREATE, null, name);
    }
}
