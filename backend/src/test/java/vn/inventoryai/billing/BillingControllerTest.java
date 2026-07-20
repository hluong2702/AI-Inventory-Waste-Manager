package vn.inventoryai.billing;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BillingControllerTest {

    @Test
    void directPlanChangeEndpointIsNotExposed() throws Exception {
        BillingController controller = new BillingController(mock(BillingService.class));
        MockMvc mockMvc = standaloneSetup(controller).build();

        mockMvc.perform(patch("/api/billing/plan")
                        .contentType("application/json")
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isNotFound());
    }
}
