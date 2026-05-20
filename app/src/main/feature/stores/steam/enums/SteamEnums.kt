package com.winlator.cmod.feature.stores.steam.enums

import java.util.EnumSet

/**
 * In-house replacements for the handful of `in.dragonbra.javasteam.enums.*`
 * enums the app still depended on (Phase 9 — dropping the JavaSteam
 * dependency). Each mirrors JavaSteam's API exactly — `code()`, `from(Int)`,
 * and for bitflag enums the `EnumSet`-based `from`/`code` pair — so consuming
 * code only needs its import line swapped. Integer codes are identical to
 * Steam's wire/DB values, so persisted data is unaffected.
 */

enum class EPersonaState(private val codeValue: Int) {
    Offline(0),
    Online(1),
    Busy(2),
    Away(3),
    Snooze(4),
    LookingToTrade(5),
    LookingToPlay(6),
    Invisible(7),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EPersonaState? = entries.firstOrNull { it.codeValue == code }
    }
}

enum class EFriendRelationship(private val codeValue: Int) {
    None(0),
    Blocked(1),
    RequestRecipient(2),
    Friend(3),
    RequestInitiator(4),
    Ignored(5),
    IgnoredFriend(6),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EFriendRelationship? = entries.firstOrNull { it.codeValue == code }
    }
}

enum class ELicenseType(private val codeValue: Int) {
    NoLicense(0),
    SinglePurchase(1),
    SinglePurchaseLimitedUse(2),
    RecurringCharge(3),
    RecurringChargeLimitedUse(4),
    RecurringChargeLimitedUseWithOverages(5),
    RecurringOption(6),
    LimitedUseDelayedActivation(7),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): ELicenseType? = entries.firstOrNull { it.codeValue == code }
    }
}

/**
 * Partial — only the [EResult] codes the app actually references. [from]
 * returns null for any other code (callers fall back, e.g. `?: Fail`).
 */
enum class EResult(private val codeValue: Int) {
    OK(1),
    Fail(2),
    NoConnection(3),
    InvalidPassword(5),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EResult? = entries.firstOrNull { it.codeValue == code }
    }
}

/** Partial — only the [EOSType] codes the app references. */
enum class EOSType(private val codeValue: Int) {
    Unknown(-1),
    AndroidUnknown(-500),
    WinUnknown(0),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EOSType? = entries.firstOrNull { it.codeValue == code }
    }
}

enum class EPaymentMethod(private val codeValue: Int) {
    None(0),
    ActivationCode(1),
    CreditCard(2),
    Giropay(3),
    PayPal(4),
    Ideal(5),
    PaySafeCard(6),
    Sofort(7),
    GuestPass(8),
    WebMoney(9),
    MoneyBookers(10),
    AliPay(11),
    Yandex(12),
    Kiosk(13),
    Qiwi(14),
    GameStop(15),
    HardwarePromo(16),
    MoPay(17),
    BoletoBancario(18),
    BoaCompraGold(19),
    BancoDoBrasilOnline(20),
    ItauOnline(21),
    BradescoOnline(22),
    Pagseguro(23),
    VisaBrazil(24),
    AmexBrazil(25),
    Aura(26),
    Hipercard(27),
    MastercardBrazil(28),
    DinersCardBrazil(29),
    AuthorizedDevice(30),
    MOLPoints(31),
    ClickAndBuy(32),
    Beeline(33),
    Konbini(34),
    EClubPoints(35),
    CreditCardJapan(36),
    BankTransferJapan(37),
    PayEasy(38),
    Zong(39),
    CultureVoucher(40),
    BookVoucher(41),
    HappymoneyVoucher(42),
    ConvenientStoreVoucher(43),
    GameVoucher(44),
    Multibanco(45),
    Payshop(46),
    MaestroBoaCompra(47),
    OXXO(48),
    ToditoCash(49),
    Carnet(50),
    SPEI(51),
    ThreePay(52),
    IsBank(53),
    Garanti(54),
    Akbank(55),
    YapiKredi(56),
    Halkbank(57),
    BankAsya(58),
    Finansbank(59),
    DenizBank(60),
    PTT(61),
    CashU(62),
    SantanderRio(63),
    AutoGrant(64),
    WebMoneyJapan(65),
    OneCard(66),
    PSE(67),
    Exito(68),
    Efecty(69),
    Paloto(70),
    PinValidda(71),
    MangirKart(72),
    BancoCreditoDePeru(73),
    BBVAContinental(74),
    SafetyPay(75),
    PagoEfectivo(76),
    Trustly(77),
    UnionPay(78),
    BitCoin(79),
    LicensedSite(80),
    BitCash(81),
    NetCash(82),
    Nanaco(83),
    Tenpay(84),
    WeChat(85),
    CashonDelivery(86),
    CreditCardNodwin(87),
    DebitCardNodwin(88),
    NetBankingNodwin(89),
    CashCardNodwin(90),
    WalletNodwin(91),
    MobileDegica(92),
    Naranja(93),
    Cencosud(94),
    Cabal(95),
    PagoFacil(96),
    Rapipago(97),
    BancoNacionaldeCostaRica(98),
    BancoPoplar(99),
    RedPagos(100),
    SPE(101),
    Multicaja(102),
    RedCompra(103),
    ZiraatBank(104),
    VakiflarBank(105),
    KuveytTurkBank(106),
    EkonomiBank(107),
    Pichincha(108),
    PichinchaCash(109),
    Przelewy24(110),
    Trustpay(111),
    POLi(112),
    MercadoPago(113),
    PayU(114),
    VTCPayWallet(115),
    MrCash(116),
    EPS(117),
    Interac(118),
    VTCPayCards(119),
    VTCPayOnlineBanking(120),
    VisaElectronBoaCompra(121),
    CafeFunded(122),
    OCA(123),
    Lider(124),
    WebMoneySteamCardJapan(125),
    WebMoneySteamCardTopUpJapan(126),
    Toss(127),
    Wallet(128),
    Valve(129),
    MasterComp(130),
    Promotional(131),
    MasterSubscription(134),
    Payco(135),
    MobileWalletJapan(136),
    BoletoFlash(137),
    PIX(138),
    GCash(139),
    KakaoPay(140),
    Dana(141),
    TrueMoney(142),
    TouchnGo(143),
    LinePay(144),
    MerPay(145),
    PayPay(146),
    AlfaClick(147),
    Sberbank(148),
    YooMoney(149),
    Tinkoff(150),
    CashInCIS(151),
    AuPAY(152),
    AliPayHK(153),
    NaverPay(154),
    Linkaja(155),
    ShopeePay(156),
    GrabPay(157),
    PayNow(158),
    OnlineBankingThailand(159),
    CashOptionsThailand(160),
    OEMTicket(256),
    Split(512),
    Complimentary(1024),
    FamilyGroup(1025),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EPaymentMethod? = entries.firstOrNull { it.codeValue == code }
    }
}

enum class EDepotFileFlag(private val codeValue: Int) {
    UserConfig(1),
    VersionedUserConfig(2),
    Encrypted(4),
    ReadOnly(8),
    Hidden(16),
    Executable(32),
    Directory(64),
    CustomExecutable(128),
    InstallScript(256),
    Symlink(512),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EnumSet<EDepotFileFlag> {
            val set = EnumSet.noneOf(EDepotFileFlag::class.java)
            for (e in entries) if ((e.codeValue and code) == e.codeValue) set.add(e)
            return set
        }

        fun code(flags: EnumSet<EDepotFileFlag>): Int {
            var c = 0
            for (f in flags) c = c or f.codeValue
            return c
        }
    }
}

enum class ELicenseFlags(private val codeValue: Int) {
    None(0),
    Renew(0x01),
    RenewalFailed(0x02),
    Pending(0x04),
    Expired(0x08),
    CancelledByUser(0x10),
    CancelledByAdmin(0x20),
    LowViolenceContent(0x40),
    ImportedFromSteam2(0x80),
    ForceRunRestriction(0x100),
    RegionRestrictionExpired(0x200),
    CancelledByFriendlyFraudLock(0x400),
    NotActivated(0x800),
    PendingRefund(0x2000),
    Borrowed(0x4000),
    ReleaseStateOverride(0x8000),
    CancelledByPartner(0x40000),
    NonPermanent(0x80000),
    PreferredOwner(0x100000),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EnumSet<ELicenseFlags> {
            val set = EnumSet.noneOf(ELicenseFlags::class.java)
            for (e in entries) if ((e.codeValue and code) == e.codeValue) set.add(e)
            return set
        }

        fun code(flags: EnumSet<ELicenseFlags>): Int {
            var c = 0
            for (f in flags) c = c or f.codeValue
            return c
        }
    }
}

enum class EClientPersonaStateFlag(private val codeValue: Int) {
    Status(1),
    PlayerName(2),
    QueryPort(4),
    SourceID(8),
    Presence(16),
    LastSeen(64),
    UserClanRank(128),
    GameExtraInfo(256),
    GameDataBlob(512),
    ClanData(1024),
    Facebook(2048),
    RichPresence(4096),
    Broadcast(8192),
    Watching(16384),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EnumSet<EClientPersonaStateFlag> {
            val set = EnumSet.noneOf(EClientPersonaStateFlag::class.java)
            for (e in entries) if ((e.codeValue and code) == e.codeValue) set.add(e)
            return set
        }

        fun code(flags: EnumSet<EClientPersonaStateFlag>): Int {
            var c = 0
            for (f in flags) c = c or f.codeValue
            return c
        }
    }
}

enum class EPersonaStateFlag(private val codeValue: Int) {
    HasRichPresence(1),
    InJoinableGame(2),
    Golden(4),
    RemotePlayTogether(8),
    ClientTypeWeb(256),
    ClientTypeMobile(512),
    ClientTypeTenfoot(1024),
    ClientTypeVR(2048),
    LaunchTypeGamepad(4096),
    LaunchTypeCompatTool(8192),
    ;

    fun code(): Int = codeValue

    companion object {
        fun from(code: Int): EnumSet<EPersonaStateFlag> {
            val set = EnumSet.noneOf(EPersonaStateFlag::class.java)
            for (e in entries) if ((e.codeValue and code) == e.codeValue) set.add(e)
            return set
        }

        fun code(flags: EnumSet<EPersonaStateFlag>): Int {
            var c = 0
            for (f in flags) c = c or f.codeValue
            return c
        }
    }
}
