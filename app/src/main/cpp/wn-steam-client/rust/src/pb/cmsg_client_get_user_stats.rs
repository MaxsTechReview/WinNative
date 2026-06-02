use crate::proto_wire::{Reader, WireType, Writer};

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct CMsgClientGetUserStats {
    pub game_id: u64,
    pub steam_id_for_user: u64,
}

impl CMsgClientGetUserStats {
    pub fn serialize(&self) -> Vec<u8> {
        let mut out = Vec::new();
        let mut w = Writer::new(&mut out);
        w.fixed64_field(1, self.game_id);
        w.fixed64_field(4, self.steam_id_for_user);
        out
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct UserStatsAchievementBlock {
    pub achievement_id: u32,
    pub unlock_time: Vec<u32>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CMsgClientGetUserStatsResponse {
    pub eresult: i32,
    pub crc_stats: u32,
    pub schema: Vec<u8>,
    pub achievement_blocks: Vec<UserStatsAchievementBlock>,
}

impl Default for CMsgClientGetUserStatsResponse {
    fn default() -> Self {
        Self {
            eresult: 2,
            crc_stats: 0,
            schema: Vec::new(),
            achievement_blocks: Vec::new(),
        }
    }
}

impl CMsgClientGetUserStatsResponse {
    pub fn deserialize(body: &[u8]) -> Option<Self> {
        let mut reader = Reader::new(body);
        let mut msg = Self::default();
        while !reader.eof() {
            let Some(tag) = reader.next_tag() else {
                return reader.ok().then_some(msg);
            };
            match tag.field_number {
                2 => msg.eresult = reader.u64()? as u32 as i32,
                3 => msg.crc_stats = reader.u32()?,
                4 => msg.schema = reader.bytes()?.to_vec(),
                6 => msg
                    .achievement_blocks
                    .push(parse_achievement_block(reader.bytes()?)?),
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

fn parse_achievement_block(body: &[u8]) -> Option<UserStatsAchievementBlock> {
    let mut reader = Reader::new(body);
    let mut block = UserStatsAchievementBlock::default();
    while !reader.eof() {
        let Some(tag) = reader.next_tag() else {
            return reader.ok().then_some(block);
        };
        match (tag.field_number, tag.wire_type) {
            (1, _) => block.achievement_id = reader.u32()?,
            (2, WireType::Fixed32) => block.unlock_time.push(reader.fixed32()?),
            (2, WireType::LengthDelimited) => {
                let mut packed = Reader::new(reader.bytes()?);
                while !packed.eof() {
                    block.unlock_time.push(packed.fixed32()?);
                }
            }
            _ => {
                if !reader.skip(tag.wire_type) {
                    return None;
                }
            }
        }
    }
    Some(block)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_unpacked_and_packed_achievement_unlocks() {
        let mut block = Vec::new();
        {
            let mut w = Writer::new(&mut block);
            w.uint32_field(1, 32);
            w.tag(2, WireType::Fixed32);
            w.raw_bytes(&10u32.to_le_bytes());
            let mut packed = Vec::new();
            packed.extend_from_slice(&20u32.to_le_bytes());
            packed.extend_from_slice(&30u32.to_le_bytes());
            w.bytes_field(2, &packed);
        }

        let mut body = Vec::new();
        {
            let mut w = Writer::new(&mut body);
            w.int32_field(2, 1);
            w.uint32_field(3, 1234);
            w.bytes_field(4, b"schema");
            w.submessage_field(6, &block);
        }

        let parsed = CMsgClientGetUserStatsResponse::deserialize(&body).unwrap();
        assert_eq!(parsed.eresult, 1);
        assert_eq!(parsed.crc_stats, 1234);
        assert_eq!(parsed.schema, b"schema");
        assert_eq!(parsed.achievement_blocks[0].unlock_time, vec![10, 20, 30]);
    }
}
