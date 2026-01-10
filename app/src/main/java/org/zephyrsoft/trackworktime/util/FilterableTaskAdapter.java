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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.zephyrsoft.trackworktime.model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * ArrayAdapter for Task objects that supports filtering/autocomplete.
 */
public class FilterableTaskAdapter extends ArrayAdapter<Task> implements Filterable {

    private final List<Task> originalTasks;
    private List<Task> filteredTasks;
    private final int resource;
    private final TaskFilter filter;

    public FilterableTaskAdapter(@NonNull Context context, int resource, @NonNull List<Task> tasks) {
        super(context, resource, new ArrayList<>(tasks));
        this.resource = resource;
        this.originalTasks = new ArrayList<>(tasks);
        this.filteredTasks = new ArrayList<>(tasks);
        this.filter = new TaskFilter();
    }

    @Override
    public int getCount() {
        return filteredTasks.size();
    }

    @Nullable
    @Override
    public Task getItem(int position) {
        if (position >= 0 && position < filteredTasks.size()) {
            return filteredTasks.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    private View createView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(resource, parent, false);
        }

        Task task = getItem(position);
        if (task != null) {
            TextView textView = (TextView) view;
            textView.setText(task.toString());
        }

        return view;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return filter;
    }

    /**
     * Update the adapter with a new list of tasks (e.g., when sort order changes).
     */
    public void updateTasks(@NonNull List<Task> tasks) {
        originalTasks.clear();
        originalTasks.addAll(tasks);
        filteredTasks.clear();
        filteredTasks.addAll(tasks);
        notifyDataSetChanged();
    }

    /**
     * Find the position of a task by ID.
     */
    public int getPositionById(Integer taskId) {
        if (taskId == null) return -1;
        for (int i = 0; i < filteredTasks.size(); i++) {
            Task task = filteredTasks.get(i);
            if (task != null && taskId.equals(task.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the original (unfiltered) list of tasks.
     */
    public List<Task> getOriginalTasks() {
        return new ArrayList<>(originalTasks);
    }

    private class TaskFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();

            if (constraint == null || constraint.length() == 0) {
                results.values = new ArrayList<>(originalTasks);
                results.count = originalTasks.size();
            } else {
                String filterString = constraint.toString().toLowerCase();
                List<Task> filtered = new ArrayList<>();

                for (Task task : originalTasks) {
                    if (task.getName() != null &&
                        task.getName().toLowerCase().contains(filterString)) {
                        filtered.add(task);
                    }
                }

                results.values = filtered;
                results.count = filtered.size();
            }

            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredTasks = (List<Task>) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    }
}
