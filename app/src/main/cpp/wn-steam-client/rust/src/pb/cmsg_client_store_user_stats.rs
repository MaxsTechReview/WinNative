use crate::proto_wire::Writer;

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct Stat {
    pub stat_id: u32,
    pub stat_value: u32,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct CMsgClientStoreUserStats2 {
    pub game_id: u64,
    pub settor_steam_id: u64,
    pub settee_steam_id: u64,
    pub crc_stats: u32,
    pub stats: Vec<Stat>,
}

impl CMsgClientStoreUserStats2 {
    pub fn serialize(&self) -> Vec<u8> {
        let mut out = Vec::new();
        let mut w = Writer::new(&mut out);
        w.fixed64_field(1, self.game_id);
        w.fixed64_field(2, self.settor_steam_id);
        w.fixed64_field(3, self.settee_steam_id);
        w.uint32_field_force(4, self.crc_stats);
        for stat in &self.stats {
            let mut sub = Vec::new();
            let mut sw = Writer::new(&mut sub);
            sw.uint32_field_force(1, stat.stat_id);
            sw.uint32_field_force(2, stat.stat_value);
            w.submessage_field(6, &sub);
        }
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto_wire::{Reader, WireType};

    #[test]
    fn keeps_zero_crc_and_zero_stat_values_present() {
        let msg = CMsgClientStoreUserStats2 {
            game_id: 7,
            settor_steam_id: 8,
            settee_steam_id: 9,
            crc_stats: 0,
            stats: vec![Stat {
                stat_id: 0,
                stat_value: 0,
            }],
        };
        let body = msg.serialize();
        let mut reader = Reader::new(&body);
        let mut saw_crc = false;
        let mut saw_stat = false;
        while !reader.eof() {
            let tag = reader.next_tag().unwrap();
            match (tag.field_number, tag.wire_type) {
                (4, WireType::Varint) => {
                    assert_eq!(reader.u32().unwrap(), 0);
                    saw_crc = true;
                }
                (6, WireType::LengthDelimited) => {
                    let sub = reader.bytes().unwrap();
                    assert_eq!(sub, &[8, 0, 16, 0]);
                    saw_stat = true;
                }
                _ => assert!(reader.skip(tag.wire_type)),
            }
        }
        assert!(saw_crc);
        assert!(saw_stat);
    }
}
