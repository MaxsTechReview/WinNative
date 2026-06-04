use crate::proto_wire::{Reader, Writer};

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct CPlayerSetRichPresenceKv {
    pub key: String,
    pub value: String,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct CPlayerSetRichPresenceRequest {
    pub appid: u32,
    pub rich_presence: Vec<CPlayerSetRichPresenceKv>,
}

impl CPlayerSetRichPresenceRequest {
    pub fn serialize(&self) -> Vec<u8> {
        let mut out = Vec::new();
        let mut w = Writer::new(&mut out);
        w.uint32_field_force(1, self.appid);
        for kv in &self.rich_presence {
            let mut sub = Vec::new();
            let mut sw = Writer::new(&mut sub);
            sw.string_field(1, &kv.key);
            sw.string_field(2, &kv.value);
            w.submessage_field(2, &sub);
        }
        out
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct CPlayerGetOwnedGamesRequest {
    pub steamid: u64,
    pub include_appinfo: bool,
    pub include_played_free_games: bool,
    pub include_free_sub: bool,
    pub include_extended_appinfo: bool,
}

impl CPlayerGetOwnedGamesRequest {
    pub fn serialize(&self) -> Vec<u8> {
        let mut out = Vec::new();
        let mut w = Writer::new(&mut out);
        w.uint64_field(1, self.steamid);
        w.bool_field(2, self.include_appinfo);
        w.bool_field(3, self.include_played_free_games);
        w.bool_field(5, self.include_free_sub);
        w.bool_field(8, self.include_extended_appinfo);
        out
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct CPlayerOwnedGame {
    pub appid: i32,
    pub name: String,
    pub playtime_2weeks: i32,
    pub playtime_forever: i32,
    pub img_icon_url: String,
    pub rtime_last_played: u32,
    pub sort_as: String,
}

impl CPlayerOwnedGame {
    pub fn deserialize(body: &[u8]) -> Option<Self> {
        let mut reader = Reader::new(body);
        let mut msg = Self::default();
        while !reader.eof() {
            let Some(tag) = reader.next_tag() else {
                return reader.ok().then_some(msg);
            };
            match tag.field_number {
                1 => msg.appid = reader.u32()? as i32,
                2 => msg.name = reader.string()?,
                3 => msg.playtime_2weeks = reader.u32()? as i32,
                4 => msg.playtime_forever = reader.u32()? as i32,
                5 => msg.img_icon_url = reader.string()?,
                11 => msg.rtime_last_played = reader.u32()?,
                13 => msg.sort_as = reader.string()?,
                _ => {
                    if !reader.skip(tag.wire_type) {
                        return None;
                    }
                }
            }
        }
        Some(msg)
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct CPlayerGetOwnedGamesResponse {
    pub game_count: u32,
    pub games: Vec<CPlayerOwnedGame>,
}

impl CPlayerGetOwnedGamesResponse {
    pub fn deserialize(body: &[u8]) -> Option<Self> {
        let mut reader = Reader::new(body);
        let mut msg = Self::default();
        while !reader.eof() {
            let Some(tag) = reader.next_tag() else {
                return reader.ok().then_some(msg);
            };
            match tag.field_number {
                1 => msg.game_count = reader.u32()?,
                2 => msg
                    .games
                    .push(CPlayerOwnedGame::deserialize(reader.bytes()?)?),
                _ => {
                    if !reader.skip(tag.wire_type) {
                        return None;
                    }
                }
            }
        }
        Some(msg)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rich_presence_force_emits_zero_appid() {
        let body = CPlayerSetRichPresenceRequest {
            appid: 0,
            rich_presence: vec![CPlayerSetRichPresenceKv {
                key: "status".to_string(),
                value: "Playing".to_string(),
            }],
        }
        .serialize();
        assert_eq!(
            body,
            [
                8, 0, 18, 17, 10, 6, b's', b't', b'a', b't', b'u', b's', 18, 7, b'P', b'l', b'a',
                b'y', b'i', b'n', b'g'
            ]
        );
    }

    #[test]
    fn parses_owned_games_response() {
        let mut game = Vec::new();
        {
            let mut w = Writer::new(&mut game);
            w.uint32_field(1, 42);
            w.string_field(2, "Half-Life");
            w.uint32_field(3, 10);
            w.uint32_field(4, 200);
            w.string_field(5, "icon");
            w.uint32_field(11, 12345);
            w.string_field(13, "Half Life");
        }

        let mut body = Vec::new();
        {
            let mut w = Writer::new(&mut body);
            w.uint32_field(1, 1);
            w.submessage_field(2, &game);
        }

        let parsed = CPlayerGetOwnedGamesResponse::deserialize(&body).unwrap();
        assert_eq!(parsed.game_count, 1);
        assert_eq!(parsed.games[0].appid, 42);
        assert_eq!(parsed.games[0].name, "Half-Life");
        assert_eq!(parsed.games[0].sort_as, "Half Life");
    }
}
