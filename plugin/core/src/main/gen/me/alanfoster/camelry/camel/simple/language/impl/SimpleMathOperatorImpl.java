// This is a generated file. Not intended for manual editing.
package me.alanfoster.camelry.camel.simple.language.impl;

import me.alanfoster.camelry.camel.simple.language.psi.SimpleMathOperator;
import me.alanfoster.camelry.camel.simple.language.psi.SimpleVisitor;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import me.alanfoster.camelry.camel.simple.language.psi.*;
public class SimpleMathOperatorImpl extends ASTWrapperPsiElement implements SimpleMathOperator {

  public SimpleMathOperatorImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SimpleVisitor) ((SimpleVisitor)visitor).visitMathOperator(this);
    else super.accept(visitor);
  }

}
