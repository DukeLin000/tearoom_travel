package com.tripmate;

import static org.assertj.core.api.Assertions.*;
import com.tripmate.identity.DevSecurity;
import com.tripmate.shared.ApiException;
import com.tripmate.shared.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoreRulesTest {
    @Test void devAuthFailsClosedOutsideExclusiveDevelopmentProfile() {
        assertThatCode(() -> DevSecurity.assertDevOnly(new String[]{"dev"})).doesNotThrowAnyException();
        assertThatThrownBy(() -> DevSecurity.assertDevOnly(new String[]{})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DevSecurity.assertDevOnly(new String[]{"prod"})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DevSecurity.assertDevOnly(new String[]{"dev","prod"})).isInstanceOf(IllegalStateException.class);
    }
    @Test void amountsAreExactAndFixedScale() {
        assertThat(Money.format(Money.parse("0.1").add(Money.parse("0.2")))).isEqualTo("0.30");
        assertThat(Money.parse("999999999999.99")).isEqualTo(new BigDecimal("999999999999.99"));
    }
    @ParameterizedTest @ValueSource(strings={"-1.00","1.001","NaN","1e2","+1"," 1","01","1000000000000.00",""})
    void invalidAmountsAreRejected(String amount) { assertThatThrownBy(() -> Money.parse(amount)).isInstanceOf(ApiException.class); }
}
