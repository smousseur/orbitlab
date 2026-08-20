package com.smousseur.orbitlab.ui.mission.wizard.step;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.ui.mission.wizard.step.RefusedPage.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RefusedPageTest {

  @Test
  @DisplayName("nothing refused mounts no page")
  void nothingRefusedMountsNoPage() {
    assertEquals(Target.NONE, RefusedPage.choose(false, false, false, false));
  }

  @Nested
  @DisplayName("a single refusal reveals the page holding it")
  class SingleRefusal {

    @Test
    @DisplayName("the node alone opens the planning page")
    void nodeAloneOpensPlanning() {
      assertEquals(Target.PLANNING, RefusedPage.choose(false, false, false, true));
    }

    @Test
    @DisplayName("the launch date alone opens the fields page")
    void dateAloneOpensFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(true, false, false, false));
    }

    @Test
    @DisplayName("the mission duration alone opens the fields page")
    void horizonAloneOpensFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(false, true, false, false));
    }

    @Test
    @DisplayName("the inclination alone opens the fields page")
    void inclinationAloneOpensFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(false, false, true, false));
    }
  }

  @Nested
  @DisplayName("the fields page wins over the node")
  class FieldsWin {

    @Test
    @DisplayName("launch date and node together open the fields page")
    void dateAndNodeOpenFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(true, false, false, true));
    }

    @Test
    @DisplayName("mission duration and node together open the fields page")
    void horizonAndNodeOpenFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(false, true, false, true));
    }

    @Test
    @DisplayName("inclination and node together open the fields page")
    void inclinationAndNodeOpenFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(false, false, true, true));
    }

    @Test
    @DisplayName("all four refused opens the fields page")
    void allRefusedOpensFields() {
      assertEquals(Target.FIELDS, RefusedPage.choose(true, true, true, true));
    }
  }
}
