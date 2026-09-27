package cl.antubank.domain.account;

/**
 * Bancos de la plaza chilena con su código oficial de plaza (usado en transferencias TEF/CCA).
 *
 * <p>Los códigos corresponden a los identificadores de institución financiera de uso común
 * en transferencias interbancarias en Chile. Se incluye un subconjunto representativo.
 */
public enum ChileanBank {

    BANCO_ESTADO("012", "BancoEstado"),
    BANCO_DE_CHILE("001", "Banco de Chile"),
    BCI("016", "Banco de Crédito e Inversiones"),
    SANTANDER_CHILE("037", "Banco Santander Chile"),
    SCOTIABANK_CHILE("014", "Scotiabank Chile"),
    ITAU_CHILE("039", "Banco Itaú Chile"),
    BICE("028", "Banco BICE"),
    SECURITY("049", "Banco Security"),
    FALABELLA("051", "Banco Falabella"),
    RIPLEY("053", "Banco Ripley");

    private final String plazaCode;
    private final String displayName;

    ChileanBank(String plazaCode, String displayName) {
        this.plazaCode = plazaCode;
        this.displayName = displayName;
    }

    /**
     * @return código de plaza del banco (para transferencias interbancarias).
     */
    public String plazaCode() {
        return plazaCode;
    }

    /**
     * @return nombre comercial del banco.
     */
    public String displayName() {
        return displayName;
    }
}
