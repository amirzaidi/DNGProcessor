package amirz.dngprocessor;

import android.util.SparseIntArray;

public class TIFF {
    public static final int TAG_NewSubfileType = 254;
    public static final int TAG_ImageWidth = 256;
    public static final int TAG_ImageLength = 257;
    public static final int TAG_BitsPerSample = 258;
    public static final int TAG_Compression = 259;
    public static final int TAG_PhotometricInterpretation = 262;
    public static final int TAG_ImageDescription = 270;
    public static final int TAG_Make = 271;
    public static final int TAG_Model = 272;
    public static final int TAG_StripOffsets = 273;
    public static final int TAG_Orientation = 274;
    public static final int TAG_SamplesPerPixel = 277;
    public static final int TAG_RowsPerStrip = 278;
    public static final int TAG_StripByteCounts = 279;
    public static final int TAG_XResolution = 282;
    public static final int TAG_YResolution = 283;
    public static final int TAG_PlanarConfiguration = 284;
    public static final int TAG_ResolutionUnit = 296;
    public static final int TAG_Software = 305;
    public static final int TAG_Hardware = 306;
    public static final int TAG_CFARepeatPatternDim = 33421;
    public static final int TAG_CFAPattern = 33422;
    public static final int TAG_Copyright = 33432;
    public static final int TAG_ExposureTime = 33434;
    public static final int TAG_FNumber = 33437;
    public static final int TAG_ISOSpeedRatings = 34855;
    public static final int TAG_DateTimeOriginal = 36867;
    public static final int TAG_FocalLength = 37386;
    public static final int TAG_EPStandardID = 37398;
    public static final int TAG_DNGVersion = 50706;
    public static final int TAG_DNGBackwardVersion = 50707;
    public static final int TAG_UniqueCameraModel = 50708;
    public static final int TAG_CFAPlaneColor = 50710;
    public static final int TAG_CFALayout = 50711;
    public static final int TAG_BlackLevelRepeatDim = 50713;
    public static final int TAG_BlackLevel = 50714;
    public static final int TAG_WhiteLevel = 50717;
    public static final int TAG_DefaultScale = 50718;
    public static final int TAG_DefaultCropOrigin = 50719;
    public static final int TAG_DefaultCropSize = 50720;
    public static final int TAG_ColorMatrix1 = 50721;
    public static final int TAG_ColorMatrix2 = 50722;
    public static final int TAG_CameraCalibration1 = 50723;
    public static final int TAG_CameraCalibration2 = 50724;
    public static final int TAG_AsShotNeutral = 50728;
    public static final int TAG_CalibrationIlluminant1 = 50778;
    public static final int TAG_CalibrationIlluminant2 = 50779;
    public static final int TAG_ActiveArea = 50829;
    public static final int TAG_ForwardMatrix1 = 50964;
    public static final int TAG_ForwardMatrix2 = 50965;
    public static final int TAG_OpcodeList2 = 51009;
    public static final int TAG_OpcodeList3 = 51022;
    public static final int TAG_NoiseProfile = 51041;

    public static final int TYPE_Byte = 1;
    public static final int TYPE_String = 2;
    public static final int TYPE_UInt_16 = 3;
    public static final int TYPE_UInt_32 = 4;
    public static final int TYPE_UFrac = 5;
    public static final int TYPE_Undef = 7;
    public static final int TYPE_Frac = 10;
    public static final int TYPE_Double = 12;

    public static final SparseIntArray TYPE_SIZES = new SparseIntArray();

    static {
        TYPE_SIZES.append(TYPE_Byte, 1);
        TYPE_SIZES.append(TYPE_String, 1);
        TYPE_SIZES.append(TYPE_UInt_16, 2);
        TYPE_SIZES.append(TYPE_UInt_32, 4);
        TYPE_SIZES.append(TYPE_UFrac, 8);
        TYPE_SIZES.append(TYPE_Undef, 0);
        TYPE_SIZES.append(TYPE_Frac, 8);
        TYPE_SIZES.append(TYPE_Double, 8);
    }
}
