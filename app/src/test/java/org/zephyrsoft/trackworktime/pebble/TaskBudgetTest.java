package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class TaskBudgetTest {

    @Test
    public void percent_noBudget_isMinusOne() {
        assertThat(TaskBudget.percent(120, null)).isEqualTo(-1);
        assertThat(TaskBudget.percent(120, 0)).isEqualTo(-1);
    }

    @Test
    public void percent_roundedAndMayExceed100() {
        assertThat(TaskBudget.percent(225, 450)).isEqualTo(50);
        assertThat(TaskBudget.percent(240, 450)).isEqualTo(53);   // 53.3 -> 53
        assertThat(TaskBudget.percent(495, 450)).isEqualTo(110);  // overtime
    }

    @Test
    public void percent_negativeWorkedClampedToZero() {
        assertThat(TaskBudget.percent(-10, 450)).isEqualTo(0);
    }

    @Test
    public void suffix_budgeted_showsAllTimeTotalAndPercent() {
        // today=90, allTime=900 (15:00), budget=1200 (20:00) -> 75%
        assertThat(TaskBudget.suffix(90, 900, 1200)).isEqualTo("15:00 (75%)");
    }

    @Test
    public void suffix_notBudgeted_showsTodayOnly() {
        assertThat(TaskBudget.suffix(90, 900, null)).isEqualTo("1:30");
        assertThat(TaskBudget.suffix(0, 0, null)).isEqualTo("0:00");
    }
}
