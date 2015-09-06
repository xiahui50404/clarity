package skadistats.clarity.decoder.s2;

import skadistats.clarity.decoder.BitStream;
import skadistats.clarity.decoder.FieldReader;
import skadistats.clarity.decoder.unpacker.Unpacker;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.s2.S2DTClass;
import skadistats.clarity.model.s2.field.FieldProperties;
import skadistats.clarity.model.s2.field.FieldType;
import skadistats.clarity.util.TextTable;

public class S2FieldReader implements FieldReader<S2DTClass> {

    public static final int MAX_EDICT_BITS = 14; // # of bits needed to represent max edicts

    // Max # of edicts in a level
    public static final int MAX_EDICTS = (1 << MAX_EDICT_BITS);

    public static final int MAX_NETWORKABLE_ENTITY_BITS = MAX_EDICT_BITS; // # of bits needed to represent max networkable entities
    public static final int MAX_NETWORKABLE_ENTITY_COUNT = (1 << MAX_NETWORKABLE_ENTITY_BITS);

    public static final int MAX_NONNETWORKABLE_ENTITY_BITS = 14;
    public static final int MAX_NONNETWORKABLE_ENTITY_COUNT = (1 << MAX_NONNETWORKABLE_ENTITY_BITS);

    public static final int MAX_SNAPSHOT_ENTITIES = (MAX_NETWORKABLE_ENTITY_COUNT);
    public static final int MAX_ENTITY_BITS = ((MAX_NETWORKABLE_ENTITY_BITS >= MAX_NONNETWORKABLE_ENTITY_BITS) ? MAX_NETWORKABLE_ENTITY_BITS : MAX_NONNETWORKABLE_ENTITY_BITS);

    // Adding one here because we need to be able to store both networkable + non-networkable entities
    public static final int ENTITY_HANDLE_ENTRY_BITS = (MAX_ENTITY_BITS + 1);
    public static final int ENTITY_HANDLE_ENTRY_COUNT = (1 << ENTITY_HANDLE_ENTRY_BITS);
    public static final int ENTITY_HANDLE_MAX_INDEX = (ENTITY_HANDLE_ENTRY_COUNT - 1);
    public static final int ENTITY_HANDLE_INVALID_ENTRY_INDEX = -1;

    public static final int INVALID_EHANDLE_INDEX = (0xFFFFFFFF);
    public static final int ENTITY_SERIAL_NUMBER_BITS = (32 - ENTITY_HANDLE_ENTRY_BITS);
    public static final int ENTITY_SERIAL_NUM_SHIFT_BITS = (ENTITY_HANDLE_ENTRY_BITS);
    public static final int ENTITY_HANDLE_ENTRY_MASK = ENTITY_HANDLE_MAX_INDEX;
    public static final int ENTITY_HANDLE_SERIAL_MASK = ((1 << ENTITY_SERIAL_NUMBER_BITS) - 1);

    // ID of zero, serial number of one
    public static final int ENTITY_ID_WORLD_ENTITY = (0 + (1 << ENTITY_SERIAL_NUM_SHIFT_BITS));


    public static final HuffmanTree HUFFMAN_TREE = new HuffmanTree();

    @Override
    public int readFields(BitStream bs, S2DTClass dtClass, FieldPath[] fieldPaths, Object[] state, boolean debug) {
        if (debug) {
            return readFieldsDebug(bs, dtClass, fieldPaths, state);
        }
        FieldPath fp = new FieldPath();
        int n = 0;
        while (true) {
            FieldOpType op = HUFFMAN_TREE.decodeOp(bs);
            op.execute(fp, bs);
            if (op == FieldOpType.FieldPathEncodeFinish) {
                break;
            }
            fieldPaths[n++] = fp;
            fp = new FieldPath(fp);
        }
        for (int r = 0; r < n; r++) {
            fp = fieldPaths[r];
            Unpacker unpacker = dtClass.getUnpackerForFieldPath(fp);
            if (unpacker == null) {
                FieldProperties f = dtClass.getFieldForFieldPath(fp).getProperties();
                throw new RuntimeException(String.format("no unpacker for field %s with type %s!", f.getName(), f.getType()));
            }
            Object data = unpacker.unpack(bs);
            dtClass.setValueForFieldPath(fp, state, data);
        }
        return n;
    }


    public int readFieldsDebug(BitStream bs, S2DTClass dtClass, FieldPath[] fieldPaths, Object[] state) {
        TextTable.Builder b = new TextTable.Builder();
        b.setTitle(dtClass.getDtName());
        b.setFrame(TextTable.FRAME_COMPAT);
        b.setPadding(0, 0);
        b.addColumn("FP");
        b.addColumn("Name");
        b.addColumn("L", TextTable.Alignment.RIGHT);
        b.addColumn("H", TextTable.Alignment.RIGHT);
        b.addColumn("BC", TextTable.Alignment.RIGHT);
        b.addColumn("Flags", TextTable.Alignment.RIGHT);
        b.addColumn("Decoder");
        b.addColumn("Type");
        b.addColumn("Value");
        b.addColumn("#", TextTable.Alignment.RIGHT);
        b.addColumn("read");
        TextTable t = b.build();

        FieldPath fp = new FieldPath();
        int n = 0;
        while (true) {
            FieldOpType op = HUFFMAN_TREE.decodeOp(bs);
            op.execute(fp, bs);
            if (op == FieldOpType.FieldPathEncodeFinish) {
                break;
            }
            fieldPaths[n++] = fp;
            fp = new FieldPath(fp);
        }
        for (int r = 0; r < n; r++) {
            fp = fieldPaths[r];
            FieldProperties f = dtClass.getFieldForFieldPath(fp).getProperties();
            FieldType ft = dtClass.getTypeForFieldPath(fp);
            t.setData(r, 0, fp);
            t.setData(r, 1, dtClass.getNameForFieldPath(fp));
            t.setData(r, 2, f.getLowValue());
            t.setData(r, 3, f.getHighValue());
            t.setData(r, 4, f.getBitCount());
            t.setData(r, 5, f.getEncodeFlags() != null ? Integer.toHexString(f.getEncodeFlags()) : "-");
            t.setData(r, 7, String.format("%s%s", ft.toString(true), f.getEncoder() != null ? String.format(" {%s}", f.getEncoder()) : ""));

            int offsBefore = bs.pos();
            Unpacker unpacker = dtClass.getUnpackerForFieldPath(fp);
            if (unpacker == null) {
                System.out.format("no unpacker for field %s with type %s!", f.getName(), f.getType());
                System.exit(1);
            }
            Object data = unpacker.unpack(bs);
            dtClass.setValueForFieldPath(fp, state, data);
            t.setData(r, 6, unpacker.getClass().getSimpleName().toString());
            t.setData(r, 8, data);
            t.setData(r, 9, bs.pos() - offsBefore);
            t.setData(r, 10, bs.toString(offsBefore, bs.pos()));
        }
        t.print(System.out);
        System.out.println("");
        System.out.println("");
        return n;
    }

}
