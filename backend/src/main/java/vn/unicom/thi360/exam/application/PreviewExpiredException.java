package vn.unicom.thi360.exam.application;

import vn.unicom.thi360.exam.spi.PreviewPaperPort.PreviewGoneException;

/** A previous preview is no longer available for partial regeneration. */
public class PreviewExpiredException extends PreviewGoneException {
}
